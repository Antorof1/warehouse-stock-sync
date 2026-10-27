package com.github.antorof1.kafkaretry;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaRetryMetricsTest {
    private static final String TOPIC = "orders";
    private static final String GROUP_ID = "test-service";

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final KafkaRetryMetrics metrics = new KafkaRetryMetrics(meterRegistry, GROUP_ID);
    private final ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 0, "key", "value");
    private final Exception failure = new IllegalStateException("boom");

    @Test
    void countsEveryFailedDelivery() {
        metrics.failedDelivery(record, failure, 1);
        metrics.failedDelivery(record, failure, 2);

        assertThat(count(KafkaRetryMetrics.FAILED_DELIVERIES)).isEqualTo(2);
    }

    @Test
    void countsRecordsSentToTheDeadLetterTopic() {
        metrics.recovered(record, failure);

        assertThat(count(KafkaRetryMetrics.DEAD_LETTERED)).isEqualTo(1);
    }

    @Test
    void countsRecordsThatCouldNotBeDeadLettered() {
        metrics.recoveryFailed(record, failure, new IllegalStateException("dlt down"));

        assertThat(count(KafkaRetryMetrics.DEAD_LETTER_FAILURES)).isEqualTo(1);
    }

    private double count(String name) {
        return meterRegistry.get(name).tag("topic", TOPIC).tag("group", GROUP_ID).counter().count();
    }
}
