package com.github.antorof1.warehouse.movement.dto;

import com.github.antorof1.warehouse.movement.Movement;
import com.github.antorof1.warehouse.movement.MovementReason;

import java.time.Instant;
import java.util.UUID;

public record MovementResponse(
    UUID id,
    String sku,
    String warehouseId,
    Integer quantityDelta,
    MovementReason reason,
    Instant occurredAt
) {
    public static MovementResponse fromEntity(Movement movement) {
        return new MovementResponse(
            movement.getId(),
            movement.getSku(),
            movement.getWarehouseId(),
            movement.getQuantityDelta(),
            movement.getReason(),
            movement.getOccurredAt()
        );
    }
}
