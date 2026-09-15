package com.github.antorof1.events;

import java.time.Instant;

public record StockLevelEvent(
    String sku,
    String warehouseId,
    Integer newLevel,
    Integer previousLevel,
    Instant occurredAt
) {
}
