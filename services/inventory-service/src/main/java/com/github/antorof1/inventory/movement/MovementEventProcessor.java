package com.github.antorof1.inventory.movement;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.Topics;
import com.github.antorof1.idempotency.ProcessedEventService;
import com.github.antorof1.inventory.stock.StockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MovementEventProcessor {
    private final StockService stockService;
    private final ProcessedEventService processedEventService;

    public MovementEventProcessor(StockService stockService, ProcessedEventService processedEventService) {
        this.stockService = stockService;
        this.processedEventService = processedEventService;
    }

    @Transactional
    public void process(MovementEvent event) {
        if (!processedEventService.tryMarkProcessed(event.id(), Topics.MOVEMENT_EVENTS)) return;

        stockService.applyMovement(
            event.sku(),
            event.warehouseId(),
            event.quantityDelta(),
            event.reason(),
            event.occurredAt()
        );
    }
}
