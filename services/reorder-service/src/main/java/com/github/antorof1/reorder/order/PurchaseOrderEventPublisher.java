package com.github.antorof1.reorder.order;

import com.github.antorof1.events.PurchaseOrderEvent;
import com.github.antorof1.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PurchaseOrderEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PurchaseOrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPurchaseOrderEvent(PurchaseOrderEvent event) {
        kafkaTemplate.send(Topics.PURCHASE_ORDER_EVENTS, event.sku(), event)
            .whenComplete((result, e) -> {
                if (e == null) return;

                log.error("Failed to publish stock level event id={} sku={}", event.id(), event.sku(), e);
            });
    }
}
