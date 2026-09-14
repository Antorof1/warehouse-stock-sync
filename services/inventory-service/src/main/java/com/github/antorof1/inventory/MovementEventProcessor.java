package com.github.antorof1.inventory;

import com.github.antorof1.events.MovementEvent;
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
        if (processedEventService.isProcessed(event.id())) return;

        stockService.applyMovement(event.sku(), event.warehouseId(), event.quantityDelta());
        processedEventService.markProcessed(event.id());
    }
}
