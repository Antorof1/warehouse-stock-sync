package com.github.antorof1.inventory.utils;

import com.github.antorof1.events.StockLevelEvent;
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

public final class StockLevelTopicConsumer<V> implements AutoCloseable {
    public static final String STOCK_LEVEL_CHANGED_TYPE = "inventory.stock-level.changed";

    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(20);

    private final Consumer<String, V> consumer;

    private StockLevelTopicConsumer(Consumer<String, V> consumer) {
        this.consumer = consumer;
        consumer.subscribe(List.of(Topics.STOCK_LEVEL_EVENTS));
        consumer.poll(POLL_INTERVAL);
    }

    public static StockLevelTopicConsumer<StockLevelEvent> withDownstreamServiceSettings(String bootstrapServers) {
        Map<String, Object> properties = baseProperties(bootstrapServers);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class);
        properties.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.github.antorof1.events");
        properties.put(
            JacksonJsonDeserializer.TYPE_MAPPINGS,
            STOCK_LEVEL_CHANGED_TYPE + ":" + StockLevelEvent.class.getName()
        );

        return new StockLevelTopicConsumer<>(new KafkaConsumer<>(properties));
    }

    public static StockLevelTopicConsumer<String> withRawPayloads(String bootstrapServers) {
        Map<String, Object> properties = baseProperties(bootstrapServers);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        return new StockLevelTopicConsumer<>(new KafkaConsumer<>(properties));
    }

    private static Map<String, Object> baseProperties(String bootstrapServers) {
        Map<String, Object> properties = KafkaTestUtils.consumerProps(
            bootstrapServers, "inventory-tests-" + UUID.randomUUID(), false
        );
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return properties;
    }

    public ConsumerRecord<String, V> awaitRecordFor(String sku) {
        return findRecordFor(sku, AWAIT_TIMEOUT)
            .orElseThrow(() -> new AssertionError("No stock level event published for sku " + sku));
    }

    public List<ConsumerRecord<String, V>> awaitRecordsFor(String sku, int count) {
        List<ConsumerRecord<String, V>> matches = new ArrayList<>();
        Instant deadline = Instant.now().plus(AWAIT_TIMEOUT);

        while (Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<String, V> record : consumer.poll(POLL_INTERVAL)) {
                if (!sku.equals(record.key())) continue;

                matches.add(record);
                if (matches.size() == count) return matches;
            }
        }

        throw new AssertionError(
            "Expected " + count + " stock level events for sku " + sku + " but saw " + matches.size()
        );
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
