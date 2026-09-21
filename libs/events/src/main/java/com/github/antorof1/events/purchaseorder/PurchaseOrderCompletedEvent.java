package com.github.antorof1.events.purchaseorder;

import java.time.Instant;
import java.util.UUID;

public record PurchaseOrderCompletedEvent(
    UUID id,
    String sku,
    String warehouseId,
    Integer orderedQuantity,
    Instant placedAt,
    Instant occurredAt
) {
}
