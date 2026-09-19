package com.github.antorof1.inventory.stock;

import com.fasterxml.uuid.Generators;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.StockLevelEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
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
    public void applyMovement(UUID movementId,
                              String sku,
                              String warehouseId, Integer quantityDelta, MovementReason reason, Instant occurredAt) {
        StockId id = new StockId(sku, warehouseId);
        Optional<Stock> optionalStock = stockRepository.findById(id);

        Stock stock = optionalStock.orElse(new Stock(id, 0));

        Integer oldQuantity = stock.getQuantity();
        stock.setQuantity(oldQuantity + quantityDelta);

        stockRepository.save(stock);

        StockLevelEvent event = new StockLevelEvent(
            Generators.timeBasedEpochGenerator().generate(),
            sku,
            warehouseId,
            stock.getQuantity(),
            oldQuantity,
            reason,
            occurredAt
        );
        publisher.publishEvent(event);
    }
}
