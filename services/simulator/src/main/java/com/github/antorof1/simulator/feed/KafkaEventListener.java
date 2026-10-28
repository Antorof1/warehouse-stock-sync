package com.github.antorof1.simulator.feed;

import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderCompletedEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderExpiredEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderPlacedEvent;
import com.github.antorof1.simulator.state.SimulationState;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KafkaEventListener implements ConsumerSeekAware {
    public static final String TOPIC_PATTERN = "(warehouse|inventory|reorder)\\..+";
    public static final String DEAD_LETTER_SUFFIX = ".dlt";

    private static final Logger log = LoggerFactory.getLogger(KafkaEventListener.class);

    private final SimulationState state;
    private final EventFeed feed;
    private final JsonMapper jsonMapper;
    private final Set<TopicPartition> replayed = ConcurrentHashMap.newKeySet();

    public KafkaEventListener(SimulationState state, EventFeed feed, JsonMapper jsonMapper) {
        this.state = state;
        this.feed = feed;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        List<TopicPartition> firstSeen = assignments.keySet().stream()
            .filter(replayed::add)
            .toList();

        if (!firstSeen.isEmpty()) {
            callback.seekToBeginning(firstSeen);
        }
    }

    @KafkaListener(topicPattern = TOPIC_PATTERN)
    public void onRecord(ConsumerRecord<String, String> record) {
        String type = header(record, EventTypes.HEADER);
        JsonNode payload = parse(record.value());
        FeedEntry.DeadLetter deadLetter = deadLetter(record);

        if (deadLetter != null) {
            state.onDeadLetter(record.topic(), record.key());
        } else if (payload != null && type != null) {
            apply(type, payload);
        }

        feed.append(new FeedEntry(
            record.topic() + "-" + record.partition() + "-" + record.offset(),
            record.topic(),
            record.partition(),
            record.offset(),
            record.key(),
            type,
            Instant.ofEpochMilli(record.timestamp()),
            payload,
            payload == null ? record.value() : null,
            deadLetter
        ));
    }

    private void apply(String type, JsonNode payload) {
        try {
            switch (type) {
                case EventTypes.STOCK_LEVEL_CHANGED ->
                    state.onStockLevel(jsonMapper.treeToValue(payload, StockLevelEvent.class));
                case EventTypes.PURCHASE_ORDER_PLACED ->
                    state.onOrderPlaced(jsonMapper.treeToValue(payload, PurchaseOrderPlacedEvent.class));
                case EventTypes.PURCHASE_ORDER_COMPLETED ->
                    state.onOrderCompleted(jsonMapper.treeToValue(payload, PurchaseOrderCompletedEvent.class));
                case EventTypes.PURCHASE_ORDER_EXPIRED ->
                    state.onOrderExpired(jsonMapper.treeToValue(payload, PurchaseOrderExpiredEvent.class));
                default -> {
                }
            }
        } catch (RuntimeException e) {
            // A malformed event mustn't stop the feed
            log.debug("Can't apply {} event to the simulation state: {}", type, e.getMessage());
        }
    }

    private JsonNode parse(String value) {
        if (value == null) return null;

        try {
            JsonNode node = jsonMapper.readTree(value);
            return node.isObject() || node.isArray() ? node : null;
        } catch (JacksonException e) {
            return null;
        }
    }

    private static FeedEntry.DeadLetter deadLetter(ConsumerRecord<String, String> record) {
        String topic = record.topic();
        if (!topic.endsWith(DEAD_LETTER_SUFFIX)) return null;

        String originalTopic = header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC);
        String withoutSuffix = topic.substring(0, topic.length() - DEAD_LETTER_SUFFIX.length());
        String group = originalTopic != null && withoutSuffix.startsWith(originalTopic + ".")
            ? withoutSuffix.substring(originalTopic.length() + 1)
            : null;

        return new FeedEntry.DeadLetter(originalTopic, group, header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE));
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);

        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
