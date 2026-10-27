package com.github.antorof1.kafkaretry;

import com.github.antorof1.kafkaretry.testapp.TestEventListener;
import com.github.antorof1.kafkaretry.utils.TopicConsumer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.github.antorof1.kafkaretry.utils.KafkaRetryUtils.DEAD_LETTER_TOPIC;
import static com.github.antorof1.kafkaretry.utils.KafkaRetryUtils.GROUP_ID;
import static com.github.antorof1.kafkaretry.utils.KafkaRetryUtils.INITIAL_INTERVAL_MILLIS;
import static com.github.antorof1.kafkaretry.utils.KafkaRetryUtils.MAX_RETRIES;
import static com.github.antorof1.kafkaretry.utils.KafkaRetryUtils.TOPIC;
import static com.github.antorof1.kafkaretry.utils.KafkaRetryUtils.TYPE;
import static com.github.antorof1.kafkaretry.utils.KafkaRetryUtils.payload;
import static com.github.antorof1.kafkaretry.utils.KafkaRetryUtils.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@KafkaRetryIntegrationTest
class DeadLetterIntegrationTest {
    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration QUIET_WINDOW = Duration.ofSeconds(3);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private TestEventListener listener;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Autowired
    private MeterRegistry meterRegistry;

    private TopicConsumer deadLetters;

    @BeforeEach
    void awaitListenerAssignment() {
        await().atMost(DELIVERY_TIMEOUT).until(() -> listenerRegistry.getListenerContainers().stream()
            .map(MessageListenerContainer::getAssignedPartitions)
            .allMatch(partitions -> partitions != null && !partitions.isEmpty()));

        deadLetters = new TopicConsumer(kafkaContainer.getBootstrapServers(), DEAD_LETTER_TOPIC);
    }

    @AfterEach
    void closeDeadLetterConsumer() {
        deadLetters.close();
    }

    @Test
    void processesAHealthyEventWithoutDeadLettering() {
        String key = uniqueKey("ok");

        publish(key, payload(key));

        await().atMost(DELIVERY_TIMEOUT).until(() -> listener.processed(key));
        assertThat(listener.attemptsFor(key)).hasSize(1);
        assertThat(deadLetters.collectRecordsFor(key, QUIET_WINDOW)).isEmpty();
    }

    @Test
    void recoversAnEventThatSucceedsOnRetry() {
        String key = uniqueKey("flaky");

        publish(key, payload(key));

        await().atMost(DELIVERY_TIMEOUT).until(() -> listener.processed(key));
        assertThat(listener.attemptsFor(key)).hasSize(2);
        assertThat(deadLetters.collectRecordsFor(key, QUIET_WINDOW)).isEmpty();
    }

    @Test
    void deadLettersAnEventOnceItsRetriesAreExhausted() {
        String key = uniqueKey("broken");

        publish(key, payload(key));

        ConsumerRecord<String, String> deadLetter = deadLetters.awaitRecordFor(key);
        assertThat(listener.attemptsFor(key)).hasSize(MAX_RETRIES + 1);
        assertThat(deadLetter.value()).contains("\"key\":\"" + key + "\"");
        assertThat(header(deadLetter, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(TOPIC);
        assertThat(header(deadLetter, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP)).isEqualTo(GROUP_ID);
        assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_MESSAGE)).contains("broken event " + key);
        assertThat(header(deadLetter, "__TypeId__")).isEqualTo(TYPE);
    }

    @Test
    void countsFailedDeliveriesAndDeadLetteredRecords() {
        String key = uniqueKey("broken");
        double failedBefore = count(KafkaRetryMetrics.FAILED_DELIVERIES);
        double deadLetteredBefore = count(KafkaRetryMetrics.DEAD_LETTERED);

        publish(key, payload(key));

        deadLetters.awaitRecordFor(key);
        await().atMost(DELIVERY_TIMEOUT).untilAsserted(() -> {
            assertThat(count(KafkaRetryMetrics.FAILED_DELIVERIES)).isGreaterThanOrEqualTo(failedBefore + MAX_RETRIES + 1);
            assertThat(count(KafkaRetryMetrics.DEAD_LETTERED)).isGreaterThanOrEqualTo(deadLetteredBefore + 1);
        });
    }

    @Test
    void backsOffExponentiallyBetweenRetries() {
        String key = uniqueKey("broken");

        publish(key, payload(key));

        deadLetters.awaitRecordFor(key);
        List<Instant> attempts = listener.attemptsFor(key);
        assertThat(Duration.between(attempts.get(0), attempts.get(1)))
            .isGreaterThanOrEqualTo(Duration.ofMillis(INITIAL_INTERVAL_MILLIS));
        assertThat(Duration.between(attempts.get(1), attempts.get(2)))
            .isGreaterThanOrEqualTo(Duration.ofMillis(INITIAL_INTERVAL_MILLIS * 2));
    }

    @Test
    void deadLettersANotRetryableFailureWithoutRetrying() {
        String key = uniqueKey("rejected");

        publish(key, payload(key));

        ConsumerRecord<String, String> deadLetter = deadLetters.awaitRecordFor(key);
        assertThat(listener.attemptsFor(key)).hasSize(1);
        assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_MESSAGE)).contains("rejected event " + key);
    }

    @Test
    void deadLettersTheRawBytesOfAnUndeserializableEventAndKeepsConsuming() {
        String poisonKey = uniqueKey("broken");
        String nextKey = uniqueKey("ok");
        String poison = "{not json";

        publish(poisonKey, poison);
        publish(nextKey, payload(nextKey));

        ConsumerRecord<String, String> deadLetter = deadLetters.awaitRecordFor(poisonKey);
        assertThat(deadLetter.value()).isEqualTo(poison);
        assertThat(header(deadLetter, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(TOPIC);
        assertThat(listener.attemptsFor(poisonKey)).isEmpty();
        await().atMost(DELIVERY_TIMEOUT).until(() -> listener.processed(nextKey));
    }

    private void publish(String key, String payload) {
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, key, payload);
        record.headers().add("__TypeId__", TYPE.getBytes(StandardCharsets.UTF_8));

        try {
            kafkaTemplate.send(record).get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish " + key, e);
        }
    }

    private double count(String name) {
        Counter counter = meterRegistry.find(name).tag("topic", TOPIC).tag("group", GROUP_ID).counter();

        return counter == null ? 0 : counter.count();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);

        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
