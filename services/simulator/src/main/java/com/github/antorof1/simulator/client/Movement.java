package com.github.antorof1.simulator.client;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.simulator.state.StockKey;

public record Movement(
    String sku,
    String warehouseId,
    Integer quantityDelta,
    MovementReason reason
) {
    public static Movement of(StockKey key, int quantityDelta, MovementReason reason) {
        return new Movement(key.sku(), key.warehouseId(), quantityDelta, reason);
    }
}
