package com.github.antorof1.notification.event;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderPlacedEvent;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.Topics;
import com.github.antorof1.notification.dispatch.NotificationDispatcher;
import com.github.antorof1.notification.idempotency.ProcessedEventService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EventListeners {
    private final ProcessedEventService processedEventService;
    private final NotificationDispatcher dispatcher;

    public EventListeners(ProcessedEventService processedEventService, NotificationDispatcher dispatcher) {
        this.processedEventService = processedEventService;
        this.dispatcher = dispatcher;
    }

    @KafkaListener(topics = Topics.MOVEMENT_EVENTS)
    public void onMovementEvent(MovementEvent event) {
        if (!processedEventService.tryMarkProcessed(event.id(), Topics.MOVEMENT_EVENTS)) return;

        dispatcher.process(event);
    }

    @KafkaListener(topics = Topics.STOCK_LEVEL_EVENTS)
    public void onStockLevelEvent(StockLevelEvent event) {
        if (!processedEventService.tryMarkProcessed(event.id(), Topics.STOCK_LEVEL_EVENTS)) return;

        dispatcher.process(event);
    }
}
