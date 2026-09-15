package com.github.antorof1.inventory.stock;

import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class StockLevelEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(StockLevelEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public StockLevelEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockLevelEvent(StockLevelEvent event) {
        kafkaTemplate.send(Topics.STOCK_LEVEL_EVENTS, event.sku(), event)
            .whenComplete((result, e) -> {
                if (e == null) return;

                log.error("Failed to publish stock level event sku={}", event.sku(), e);
            });
    }
}
