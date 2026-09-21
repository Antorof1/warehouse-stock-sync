package com.github.antorof1.events.purchaseorder;

import java.time.Instant;
import java.util.UUID;

public record PurchaseOrderPlacedEvent(
    UUID id,
    String sku,
    String warehouseId,
    Integer quantity,
    Instant occurredAt
) {
}
