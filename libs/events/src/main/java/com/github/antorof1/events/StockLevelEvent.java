package com.github.antorof1.events;

import java.time.Instant;
import java.util.UUID;

public record StockLevelEvent(
    UUID id,
    String sku,
    String warehouseId,
    Integer newLevel,
    Integer previousLevel,
    MovementReason reason,
    Instant occurredAt
) {
}
