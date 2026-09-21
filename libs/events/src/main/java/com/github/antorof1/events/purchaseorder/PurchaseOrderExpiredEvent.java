package com.github.antorof1.events.purchaseorder;

import java.time.Instant;
import java.util.UUID;

public record PurchaseOrderExpiredEvent(
    UUID id,
    String sku,
    String warehouseId,
    Integer orderedQuantity,
    Integer receivedQuantity,
    Instant occurredAt
) {
}
