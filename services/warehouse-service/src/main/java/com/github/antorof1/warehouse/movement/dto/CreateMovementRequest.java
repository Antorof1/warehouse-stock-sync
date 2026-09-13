package com.github.antorof1.warehouse.movement.dto;

import com.github.antorof1.warehouse.movement.MovementReason;

public record CreateMovementRequest(
    String sku,
    String warehouseId,
    Integer quantityDelta,
    MovementReason reason
) {
}
