package com.github.antorof1.kafkaretry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RetryListener;

public class KafkaRetryMetrics implements RetryListener {
    static final String FAILED_DELIVERIES = "kafka.consumer.failed.deliveries";
    static final String DEAD_LETTERED = "kafka.consumer.dead.lettered";
    static final String DEAD_LETTER_FAILURES = "kafka.consumer.dead.letter.failures";

    private final MeterRegistry meterRegistry;
    private final String groupId;

    public KafkaRetryMetrics(MeterRegistry meterRegistry, String groupId) {
        this.meterRegistry = meterRegistry;
        this.groupId = groupId;
    }

    @Override
    public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
        count(FAILED_DELIVERIES, record);
    }

    @Override
    public void recovered(ConsumerRecord<?, ?> record, Exception ex) {
        count(DEAD_LETTERED, record);
    }

    @Override
    public void recoveryFailed(ConsumerRecord<?, ?> record, Exception original, Exception failure) {
        count(DEAD_LETTER_FAILURES, record);
    }

    private void count(String name, ConsumerRecord<?, ?> record) {
        Counter.builder(name)
            .tag("topic", record.topic())
            .tag("group", groupId)
            .register(meterRegistry)
            .increment();
    }
}
