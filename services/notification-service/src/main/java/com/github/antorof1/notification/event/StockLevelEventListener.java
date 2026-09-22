package com.github.antorof1.notification.event;

import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@KafkaListener(topics = Topics.STOCK_LEVEL_EVENTS)
public class StockLevelEventListener {
    private static final Logger log = LoggerFactory.getLogger(StockLevelEventListener.class);

    private final EventProcessor eventProcessor;

    public StockLevelEventListener(EventProcessor eventProcessor) {
        this.eventProcessor = eventProcessor;
    }

    @KafkaHandler
    public void onStockLevelEvent(StockLevelEvent event) {
        eventProcessor.handle(event.id(), Topics.STOCK_LEVEL_EVENTS, event);
    }

    @KafkaHandler(isDefault = true)
    public void onUnhandledEvent(Object event) {
        log.debug("No notification handling for {}", event.getClass().getSimpleName());
    }
}
