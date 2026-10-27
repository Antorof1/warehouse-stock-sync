package com.github.antorof1.notification.event;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.Topics;
import com.github.antorof1.idempotency.ProcessedEventService;
import com.github.antorof1.kafkaretry.DeadLetterTopics;
import com.github.antorof1.notification.IntegrationTest;
import com.github.antorof1.notification.utils.CapturingSender;
import com.github.antorof1.notification.utils.DeadLetterTopicConsumer;
import com.github.antorof1.notification.utils.EventProducer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.github.antorof1.notification.utils.DeadLetterTopicConsumer.header;
import static com.github.antorof1.notification.utils.NotificationUtils.movementEvent;
import static com.github.antorof1.notification.utils.NotificationUtils.uniqueSku;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@IntegrationTest
@TestPropertySource(properties = {
    "app.kafka.retry.max-retries=" + NotificationDeadLetterIntegrationTest.MAX_RETRIES,
    "app.kafka.retry.initial-interval=100ms"
})
class NotificationDeadLetterIntegrationTest {
    static final int MAX_RETRIES = 2;

    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(30);
    private static final String MOVEMENT_RECORDED_TYPE = "warehouse.movement.recorded";
    private static final String DEAD_LETTER_TOPIC = DeadLetterTopics.of(Topics.MOVEMENT_EVENTS, "notification-service");

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private CapturingSender sender;

    @Autowired
    private KafkaContainer kafkaContainer;

    @MockitoSpyBean
    private ProcessedEventService processedEventService;

    private EventProducer producer;
    private DeadLetterTopicConsumer deadLetters;

    @BeforeEach
    void awaitListenerAssignment() {
        producer = new EventProducer(kafkaTemplate);
        sender.reset();

        await().atMost(DELIVERY_TIMEOUT).until(() -> listenerRegistry.getListenerContainers().stream()
            .map(MessageListenerContainer::getAssignedPartitions)
            .allMatch(partitions -> partitions != null && !partitions.isEmpty()));

        deadLetters = new DeadLetterTopicConsumer(kafkaContainer.getBootstrapServers(), DEAD_LETTER_TOPIC);
    }

    @AfterEach
    void closeDeadLetterConsumer() {
        deadLetters.close();
        sender.reset();
    }

    @Test
    void deadLettersAnEventWhoseProcessedMarkerCannotBeRecorded() {
        String sku = uniqueSku();
        MovementEvent event = movementEvent(sku, -2, MovementReason.DAMAGE);
        willThrow(new DataAccessResourceFailureException("processed events are unavailable"))
            .given(processedEventService).tryMarkProcessed(eq(event.id()), eq(Topics.MOVEMENT_EVENTS));

        producer.publish(Topics.MOVEMENT_EVENTS, sku, event);

        ConsumerRecord<String, String> deadLetter = deadLetters.awaitRecordFor(sku);
        assertThat(objectMapper.readValue(deadLetter.value(), MovementEvent.class)).isEqualTo(event);
        assertThat(header(deadLetter, "__TypeId__")).isEqualTo(MOVEMENT_RECORDED_TYPE);
        assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_MESSAGE)).contains("processed events are unavailable");
        verify(processedEventService, times(MAX_RETRIES + 1)).tryMarkProcessed(event.id(), Topics.MOVEMENT_EVENTS);
        assertThat(sender.attemptsFor(sku)).isEmpty();
    }

    @Test
    void deadLettersAnUndeserializableEventAndKeepsConsuming() {
        String poisonSku = uniqueSku();
        String nextSku = uniqueSku();
        String poison = "{not json";

        publishRaw(poisonSku, poison);
        producer.publish(Topics.MOVEMENT_EVENTS, nextSku, movementEvent(nextSku, -2, MovementReason.DAMAGE));

        ConsumerRecord<String, String> deadLetter = deadLetters.awaitRecordFor(poisonSku);
        assertThat(deadLetter.value()).isEqualTo(poison);
        assertThat(header(deadLetter, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(Topics.MOVEMENT_EVENTS);
        await().atMost(DELIVERY_TIMEOUT).until(() -> !sender.attemptsFor(nextSku).isEmpty());
    }

    private void publishRaw(String key, String payload) {
        Map<String, Object> properties = KafkaTestUtils.producerProps(kafkaContainer.getBootstrapServers());
        ProducerRecord<String, String> record = new ProducerRecord<>(Topics.MOVEMENT_EVENTS, key, payload);
        record.headers().add("__TypeId__", MOVEMENT_RECORDED_TYPE.getBytes(StandardCharsets.UTF_8));

        try (Producer<String, String> rawProducer =
                 new KafkaProducer<>(properties, new StringSerializer(), new StringSerializer())) {
            rawProducer.send(record).get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish " + key, e);
        }
    }
}
