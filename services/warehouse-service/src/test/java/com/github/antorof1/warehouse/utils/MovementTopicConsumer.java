package com.github.antorof1.warehouse.utils;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.Topics;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MovementTopicConsumer<V> implements AutoCloseable {
    public static final String MOVEMENT_RECORDED_TYPE = "warehouse.movement.recorded";

    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(20);

    private final Consumer<String, V> consumer;

    private MovementTopicConsumer(Consumer<String, V> consumer) {
        this.consumer = consumer;
        consumer.subscribe(List.of(Topics.MOVEMENT_EVENTS));
    }

    public static MovementTopicConsumer<MovementEvent> withInventoryServiceSettings(String bootstrapServers) {
        Map<String, Object> properties = baseProperties(bootstrapServers);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class);
        properties.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.github.antorof1.events");
        properties.put(
            JacksonJsonDeserializer.TYPE_MAPPINGS,
            MOVEMENT_RECORDED_TYPE + ":" + MovementEvent.class.getName()
        );

        return new MovementTopicConsumer<>(new KafkaConsumer<>(properties));
    }

    public static MovementTopicConsumer<String> withRawPayloads(String bootstrapServers) {
        Map<String, Object> properties = baseProperties(bootstrapServers);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        return new MovementTopicConsumer<>(new KafkaConsumer<>(properties));
    }

    private static Map<String, Object> baseProperties(String bootstrapServers) {
        Map<String, Object> properties = KafkaTestUtils.consumerProps(
            bootstrapServers, "warehouse-tests-" + UUID.randomUUID(), false
        );
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        return properties;
    }

    public ConsumerRecord<String, V> awaitRecordFor(String sku) {
        return findRecordFor(sku, AWAIT_TIMEOUT)
            .orElseThrow(() -> new AssertionError("No movement event published for sku " + sku));
    }

    public Optional<ConsumerRecord<String, V>> findRecordFor(String sku, Duration within) {
        Instant deadline = Instant.now().plus(within);

        while (Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<String, V> record : consumer.poll(POLL_INTERVAL)) {
                if (sku.equals(record.key())) return Optional.of(record);
            }
        }

        return Optional.empty();
    }

    public List<ConsumerRecord<String, V>> collectRecordsFor(String sku, Duration within) {
        List<ConsumerRecord<String, V>> matches = new ArrayList<>();
        Instant deadline = Instant.now().plus(within);

        while (Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<String, V> record : consumer.poll(POLL_INTERVAL)) {
                if (!sku.equals(record.key())) continue;

                matches.add(record);
            }
        }

        return matches;
    }

    @Override
    public void close() {
        consumer.close();
    }
}
