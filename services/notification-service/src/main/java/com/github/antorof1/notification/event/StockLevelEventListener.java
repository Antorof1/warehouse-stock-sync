package com.github.antorof1.notification.event;

import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.Topics;
import com.github.antorof1.notification.dispatch.NotificationDispatcher;
import com.github.antorof1.notification.idempotency.ProcessedEventService;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@KafkaListener(topics = Topics.STOCK_LEVEL_EVENTS)
public class StockLevelEventListener {
    private final ProcessedEventService processedEventService;
    private final NotificationDispatcher dispatcher;

    public StockLevelEventListener(ProcessedEventService processedEventService, NotificationDispatcher dispatcher) {
        this.processedEventService = processedEventService;
        this.dispatcher = dispatcher;
    }

    @KafkaHandler
    public void onStockLevelEvent(StockLevelEvent event) {
        if (!processedEventService.tryMarkProcessed(event.id(), Topics.STOCK_LEVEL_EVENTS)) return;

        dispatcher.process(event);
    }
}
