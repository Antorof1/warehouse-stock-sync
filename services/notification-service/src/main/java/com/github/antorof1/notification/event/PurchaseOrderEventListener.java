package com.github.antorof1.notification.event;

import com.github.antorof1.events.Topics;
import com.github.antorof1.events.purchaseorder.PurchaseOrderCompletedEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderExpiredEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@KafkaListener(topics = Topics.PURCHASE_ORDER_EVENTS)
public class PurchaseOrderEventListener {
    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderEventListener.class);

    private final EventProcessor eventProcessor;

    public PurchaseOrderEventListener(EventProcessor eventProcessor) {
        this.eventProcessor = eventProcessor;
    }

    @KafkaHandler
    public void onPurchaseOrderPlacedEvent(PurchaseOrderPlacedEvent event) {
        handle(event.id(), event);
    }

    @KafkaHandler
    public void onPurchaseOrderCompletedEvent(PurchaseOrderCompletedEvent event) {
        handle(event.id(), event);
    }

    @KafkaHandler
    public void onPurchaseOrderExpiredEvent(PurchaseOrderExpiredEvent event) {
        handle(event.id(), event);
    }

    private void handle(UUID id, Object event) {
        eventProcessor.handle(id, Topics.PURCHASE_ORDER_EVENTS, event);
    }

    @KafkaHandler(isDefault = true)
    public void onUnhandledEvent(Object event) {
        log.debug("No notification handling for {}", event.getClass().getSimpleName());
    }
}
