package com.github.antorof1.inventory.stock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class StockService {
    private final StockRepository stockRepository;

    public StockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Transactional
    public void applyMovement(String sku, String warehouseId, Integer quantityDelta) {
        StockId id = new StockId(sku, warehouseId);
        Optional<Stock> optionalStock = stockRepository.findById(id);

        Stock stock = optionalStock.orElse(new Stock(id, 0));

        stock.setQuantity(stock.getQuantity() + quantityDelta);

        stockRepository.save(stock);
    }
}
