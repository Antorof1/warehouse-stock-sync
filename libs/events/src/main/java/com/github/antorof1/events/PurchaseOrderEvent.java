package com.github.antorof1.events;

import java.time.Instant;
import java.util.UUID;

public record PurchaseOrderEvent(
    UUID id,
    String sku,
    String warehouseId,
    Integer quantity,
    Instant occurredAt
) {
}
