package com.github.antorof1.reorder.purchaseorder;

import com.github.antorof1.events.purchaseorder.PurchaseOrderCompletedEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderExpiredEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderPlacedEvent;
import com.github.antorof1.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
public class PurchaseOrderEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PurchaseOrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPurchaseOrderPlacedEvent(PurchaseOrderPlacedEvent event) {
        sendEvent(event, event.sku(), event.id());
    }


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPurchaseOrderCompletedEvent(PurchaseOrderCompletedEvent event) {
        sendEvent(event, event.sku(), event.id());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPurchaseOrderExpiredEvent(PurchaseOrderExpiredEvent event) {
        sendEvent(event, event.sku(), event.id());
    }

    private void sendEvent(Object event, String sku, UUID id) {
        kafkaTemplate.send(Topics.PURCHASE_ORDER_EVENTS, sku, event)
            .whenComplete((result, e) -> {
                if (e == null) return;

                log.error("Failed to publish purchase order event type={} id={} sku={}",
                    event.getClass().getSimpleName(), id, sku, e);
            });
    }
}
