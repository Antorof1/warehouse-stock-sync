package com.github.antorof1.events;

import java.time.Instant;
import java.util.UUID;

public record MovementEvent(
    UUID id,
    String sku,
    String warehouseId,
    Integer quantityDelta,
    MovementReason reason,
    Instant occurredAt
) {
}
