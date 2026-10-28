package com.github.antorof1.simulator.client;

import com.github.antorof1.simulator.state.StockKey;

public record ReorderRule(
    String sku,
    String warehouseId,
    int reorderPoint,
    int reorderUpTo
) {
    public StockKey key() {
        return new StockKey(warehouseId, sku);
    }
}
