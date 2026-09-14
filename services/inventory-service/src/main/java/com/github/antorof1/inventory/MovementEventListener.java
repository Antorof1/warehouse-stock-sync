package com.github.antorof1.inventory;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MovementEventListener {
    private final StockService stockService;

    public MovementEventListener(StockService stockService) {
        this.stockService = stockService;
    }

    @KafkaListener(groupId = "123", topics = Topics.MOVEMENT_EVENTS)
    public void onMovementEvent(MovementEvent event) {
        stockService.updateOrAddStock(event.sku(), event.warehouseId(), event.quantityDelta());
    }
}
