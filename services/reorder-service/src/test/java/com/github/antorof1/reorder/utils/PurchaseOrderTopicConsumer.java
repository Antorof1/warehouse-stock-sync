package com.github.antorof1.reorder.utils;

import com.github.antorof1.events.Topics;
import com.github.antorof1.events.purchaseorder.PurchaseOrderCompletedEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderExpiredEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderPlacedEvent;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PurchaseOrderTopicConsumer<V> implements AutoCloseable {
    public static final String PLACED_TYPE = "reorder.purchase-order.placed";
    public static final String COMPLETED_TYPE = "reorder.purchase-order.completed";
    public static final String EXPIRED_TYPE = "reorder.purchase-order.expired";

    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(20);

    private final Consumer<String, V> consumer;
    private final List<ConsumerRecord<String, V>> buffered = new ArrayList<>();

    private PurchaseOrderTopicConsumer(Consumer<String, V> consumer) {
        this.consumer = consumer;
        consumer.subscribe(List.of(Topics.PURCHASE_ORDER_EVENTS));
        consumer.poll(POLL_INTERVAL);
    }

    public static PurchaseOrderTopicConsumer<Object> withDownstreamServiceSettings(String bootstrapServers) {
        Map<String, Object> properties = baseProperties(bootstrapServers);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class);
        properties.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.github.antorof1.events*");
        properties.put(
            JacksonJsonDeserializer.TYPE_MAPPINGS,
            PLACED_TYPE + ":" + PurchaseOrderPlacedEvent.class.getName() + ","
                + COMPLETED_TYPE + ":" + PurchaseOrderCompletedEvent.class.getName() + ","
                + EXPIRED_TYPE + ":" + PurchaseOrderExpiredEvent.class.getName()
        );

        return new PurchaseOrderTopicConsumer<>(new KafkaConsumer<>(properties));
    }

    public static PurchaseOrderTopicConsumer<String> withRawPayloads(String bootstrapServers) {
        Map<String, Object> properties = baseProperties(bootstrapServers);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        return new PurchaseOrderTopicConsumer<>(new KafkaConsumer<>(properties));
    }

    private static Map<String, Object> baseProperties(String bootstrapServers) {
        Map<String, Object> properties = KafkaTestUtils.consumerProps(
            bootstrapServers, "reorder-tests-" + UUID.randomUUID(), false
        );
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return properties;
    }

    public ConsumerRecord<String, V> awaitRecordFor(String sku) {
        return findRecordFor(sku, AWAIT_TIMEOUT)
            .orElseThrow(() -> new AssertionError("No purchase order event published for sku " + sku));
    }

    public List<ConsumerRecord<String, V>> awaitRecordsFor(String sku, int count) {
        List<ConsumerRecord<String, V>> matches = new ArrayList<>();
        Instant deadline = Instant.now().plus(AWAIT_TIMEOUT);

        while (true) {
            matches.addAll(take(sku, count - matches.size()));
            if (matches.size() == count) return matches;
            if (!Instant.now().isBefore(deadline)) break;
            poll();
        }

        throw new AssertionError(
            "Expected " + count + " purchase order events for sku " + sku + " but saw " + matches.size()
        );
    }

    public Optional<ConsumerRecord<String, V>> findRecordFor(String sku, Duration within) {
        Instant deadline = Instant.now().plus(within);

        while (true) {
            List<ConsumerRecord<String, V>> match = take(sku, 1);
            if (!match.isEmpty()) return Optional.of(match.getFirst());
            if (!Instant.now().isBefore(deadline)) return Optional.empty();
            poll();
        }
    }

    public List<ConsumerRecord<String, V>> collectRecordsFor(String sku, Duration within) {
        Instant deadline = Instant.now().plus(within);

        while (Instant.now().isBefore(deadline)) {
            poll();
        }

        return take(sku, Integer.MAX_VALUE);
    }

    private void poll() {
        consumer.poll(POLL_INTERVAL).forEach(buffered::add);
    }

    private List<ConsumerRecord<String, V>> take(String sku, int max) {
        List<ConsumerRecord<String, V>> taken = new ArrayList<>();
        Iterator<ConsumerRecord<String, V>> records = buffered.iterator();

        while (records.hasNext() && taken.size() < max) {
            ConsumerRecord<String, V> record = records.next();
            if (!sku.equals(record.key())) continue;

            taken.add(record);
            records.remove();
        }

        return taken;
    }

    @Override
    public void close() {
        consumer.close();
    }
}
