package com.github.antorof1.inventory.stock;

import com.fasterxml.uuid.Generators;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.StockLevelEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StockService {
    private final StockRepository stockRepository;
    private final ApplicationEventPublisher publisher;

    public StockService(StockRepository stockRepository, ApplicationEventPublisher publisher) {
        this.stockRepository = stockRepository;
        this.publisher = publisher;
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
        publisher.publishEvent(event);
    }
}
