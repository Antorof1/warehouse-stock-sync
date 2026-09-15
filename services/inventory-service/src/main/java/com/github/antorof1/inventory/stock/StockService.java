package com.github.antorof1.inventory.stock;

import com.github.antorof1.events.StockLevelEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

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
    public void applyMovement(String sku, String warehouseId, Integer quantityDelta) {
        StockId id = new StockId(sku, warehouseId);
        Optional<Stock> optionalStock = stockRepository.findById(id);

        Stock stock = optionalStock.orElse(new Stock(id, 0));

        Integer oldQuantity = stock.getQuantity();
        stock.setQuantity(oldQuantity + quantityDelta);

        stockRepository.save(stock);

        StockLevelEvent event = new StockLevelEvent(sku, warehouseId, stock.getQuantity(), oldQuantity, Instant.now());
        publisher.publishEvent(event);
    }
}
