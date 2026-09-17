package com.github.antorof1.reorder.stock;

import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class StockLevelEventListener {
    private final StockLevelEventProcessor processor;

    public StockLevelEventListener(StockLevelEventProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(topics = Topics.STOCK_LEVEL_EVENTS)
    public void onStockLevelEvent(StockLevelEvent event) {
        processor.process(event);
    }
}
