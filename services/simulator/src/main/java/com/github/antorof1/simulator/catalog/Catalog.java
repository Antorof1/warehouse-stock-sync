package com.github.antorof1.simulator.catalog;

import com.github.antorof1.simulator.config.SimulatorProperties;
import com.github.antorof1.simulator.config.SimulatorProperties.CatalogSku;
import com.github.antorof1.simulator.state.StockKey;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class Catalog {
    private final List<String> warehouses;
    private final Map<StockKey, CatalogItem> items = new LinkedHashMap<>();

    public Catalog(SimulatorProperties properties) {
        this.warehouses = List.copyOf(properties.catalog().warehouses());

        for (String warehouseId : warehouses) {
            for (CatalogSku sku : properties.catalog().skus()) {
                StockKey key = new StockKey(warehouseId, sku.sku());
                items.put(key, new CatalogItem(key, sku.name(), sku.reorderPoint(), sku.reorderUpTo()));
            }
        }
    }

    public List<String> warehouses() {
        return warehouses;
    }

    public List<CatalogItem> items() {
        return List.copyOf(items.values());
    }

    public Optional<CatalogItem> find(StockKey key) {
        return Optional.ofNullable(items.get(key));
    }

    public boolean contains(StockKey key) {
        return items.containsKey(key);
    }

    public record CatalogItem(StockKey key, String name, int reorderPoint, int reorderUpTo) {
    }
}
