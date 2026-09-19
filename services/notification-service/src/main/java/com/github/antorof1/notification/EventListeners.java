package com.github.antorof1.notification;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.PurchaseOrderEvent;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.Topics;
import com.github.antorof1.notification.idempotency.ProcessedEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EventListeners {
    private static final Logger log = LoggerFactory.getLogger(EventListeners.class);

    private final ProcessedEventService processedEventService;

    public EventListeners(ProcessedEventService processedEventService) {
        this.processedEventService = processedEventService;
    }

    @KafkaListener(topics = Topics.MOVEMENT_EVENTS)
    public void onMovementEvent(MovementEvent event) {
        if (processedEventService.isProcessed(event.id(), Topics.MOVEMENT_EVENTS)) return;

        log.info(event.toString());

        processedEventService.markProcessed(event.id(), Topics.MOVEMENT_EVENTS);
    }

    @KafkaListener(topics = Topics.STOCK_LEVEL_EVENTS)
    public void onStockLevelEvent(StockLevelEvent event) {
        if (processedEventService.isProcessed(event.id(), Topics.STOCK_LEVEL_EVENTS)) return;

        log.info(event.toString());

        processedEventService.markProcessed(event.id(), Topics.STOCK_LEVEL_EVENTS);
    }

    @KafkaListener(topics = Topics.PURCHASE_ORDER_EVENTS)
    public void onPurchaseOrderEvent(PurchaseOrderEvent event) {
        if (processedEventService.isProcessed(event.id(), Topics.PURCHASE_ORDER_EVENTS)) return;

        log.info(event.toString());

        processedEventService.markProcessed(event.id(), Topics.PURCHASE_ORDER_EVENTS);
    }
}
