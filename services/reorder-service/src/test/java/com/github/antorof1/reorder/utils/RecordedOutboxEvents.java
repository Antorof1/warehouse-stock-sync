package com.github.antorof1.reorder.utils;

import com.github.antorof1.events.Topics;
import com.github.antorof1.events.purchaseorder.PurchaseOrderCompletedEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderExpiredEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderPlacedEvent;
import com.github.antorof1.outbox.OutboxEvent;
import com.github.antorof1.outbox.OutboxEventRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.github.antorof1.reorder.utils.PurchaseOrderTopicConsumer.COMPLETED_TYPE;
import static com.github.antorof1.reorder.utils.PurchaseOrderTopicConsumer.EXPIRED_TYPE;
import static com.github.antorof1.reorder.utils.PurchaseOrderTopicConsumer.PLACED_TYPE;

public final class RecordedOutboxEvents {
    private static final Map<Class<?>, String> TYPES = Map.of(
        PurchaseOrderPlacedEvent.class, PLACED_TYPE,
        PurchaseOrderCompletedEvent.class, COMPLETED_TYPE,
        PurchaseOrderExpiredEvent.class, EXPIRED_TYPE
    );

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public RecordedOutboxEvents(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public List<OutboxEvent> purchaseOrderRowsFor(String sku) {
        return outboxEventRepository.findAll().stream()
            .filter(row -> row.getTopic().equals(Topics.PURCHASE_ORDER_EVENTS))
            .filter(row -> row.getKey().equals(sku))
            .sorted(Comparator.comparing(OutboxEvent::getCreatedAt))
            .toList();
    }

    public <T> List<T> purchaseOrderEventsFor(String sku, Class<T> eventClass) {
        String type = TYPES.get(eventClass);

        return purchaseOrderRowsFor(sku).stream()
            .filter(row -> row.getType().equals(type))
            .map(row -> objectMapper.readValue(row.getPayload(), eventClass))
            .toList();
    }
}
