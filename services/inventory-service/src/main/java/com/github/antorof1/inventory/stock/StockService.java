package com.github.antorof1.inventory.stock;

import com.fasterxml.uuid.Generators;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.Topics;
import com.github.antorof1.outbox.OutboxEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class StockService {
    private final StockRepository stockRepository;
    private final OutboxEventService outboxEventService;

    public StockService(StockRepository stockRepository, OutboxEventService outboxEventService) {
        this.stockRepository = stockRepository;
        this.outboxEventService = outboxEventService;
    }

    @Transactional
    public void applyMovement(String sku, String warehouseId, Integer quantityDelta, MovementReason reason,
                              Instant occurredAt) {
        Integer newQuantity = stockRepository.applyDelta(sku, warehouseId, quantityDelta);
        Integer oldQuantity = newQuantity - quantityDelta;

        StockLevelEvent event = new StockLevelEvent(
            Generators.timeBasedEpochGenerator().generate(),
            sku,
            warehouseId,
            newQuantity,
            oldQuantity,
            reason,
            occurredAt
        );
        outboxEventService.record(event.id(), Topics.STOCK_LEVEL_EVENTS, event.sku(), event);
    }
}
