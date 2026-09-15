package com.github.antorof1.warehouse.movement.dto;

import com.github.antorof1.events.MovementReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMovementRequest(
    @NotBlank(message = "SKU is required")
    @Size(max = 64, message = "SKU is too long")
    String sku,

    @NotBlank(message = "Warehouse ID is required")
    @Size(max = 64, message = "Warehouse ID is too long")
    String warehouseId,

    @NotNull(message = "Quantity delta is required")
    Integer quantityDelta,

    @NotNull(message = "Reason is required")
    MovementReason reason
) {
}
