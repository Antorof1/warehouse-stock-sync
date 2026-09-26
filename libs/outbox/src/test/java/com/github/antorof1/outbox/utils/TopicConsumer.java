package com.github.antorof1.outbox.utils;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class TopicConsumer implements AutoCloseable {
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(20);

    private final Consumer<String, String> consumer;

    public TopicConsumer(String bootstrapServers) {
        Map<String, Object> properties = KafkaTestUtils.consumerProps(
            bootstrapServers, "outbox-tests-" + UUID.randomUUID(), false
        );
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        this.consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(OutboxUtils.TOPIC));
    }

    public ConsumerRecord<String, String> awaitRecordFor(String key) {
        Instant deadline = Instant.now().plus(AWAIT_TIMEOUT);

        while (Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<String, String> record : consumer.poll(POLL_INTERVAL)) {
                if (key.equals(record.key())) return record;
            }
        }

        throw new AssertionError("No record published for key " + key);
    }

    public List<ConsumerRecord<String, String>> collectRecordsFor(String key, Duration within) {
        List<ConsumerRecord<String, String>> matches = new ArrayList<>();
        Instant deadline = Instant.now().plus(within);

        while (Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<String, String> record : consumer.poll(POLL_INTERVAL)) {
                if (key.equals(record.key())) matches.add(record);
            }
        }

        return matches;
    }

    @Override
    public void close() {
        consumer.close();
    }
}
