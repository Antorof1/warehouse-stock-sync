package com.github.antorof1.inventory;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class StockService {
    private final StockRepository stockRepository;

    public StockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public Stock updateOrAddStock(String sku, String warehouseId, Integer quantityDelta) {
        StockId id = new StockId(sku, warehouseId);
        Optional<Stock> optionalStock = stockRepository.findById(id);

        Stock stock = optionalStock.orElse(new Stock(id, 0));

        stock.setQuantity(stock.getQuantity() + quantityDelta);

        return stockRepository.save(stock);
    }
}
