package com.github.antorof1.reorder.utils;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DeadLetterTopicConsumer implements AutoCloseable {
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30);

    private final Consumer<String, String> consumer;

    public DeadLetterTopicConsumer(String bootstrapServers, String topic) {
        Map<String, Object> properties = KafkaTestUtils.consumerProps(
            bootstrapServers, "reorder-tests-" + UUID.randomUUID(), false
        );
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        this.consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(topic));
    }

    public ConsumerRecord<String, String> awaitRecordFor(String key) {
        Instant deadline = Instant.now().plus(AWAIT_TIMEOUT);

        while (Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<String, String> record : consumer.poll(POLL_INTERVAL)) {
                if (key.equals(record.key())) return record;
            }
        }

        throw new AssertionError("No dead letter published for key " + key);
    }

    public static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);

        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        consumer.close();
    }
}
