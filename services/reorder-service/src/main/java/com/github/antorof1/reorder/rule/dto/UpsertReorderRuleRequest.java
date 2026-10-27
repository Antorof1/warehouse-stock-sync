package com.github.antorof1.reorder.rule.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record UpsertReorderRuleRequest(
    @NotNull(message = "Reorder point is required")
    @PositiveOrZero(message = "Reorder point can't be negative")
    Integer reorderPoint,

    @NotNull(message = "Reorder up to is required")
    @Positive(message = "Reorder up to must be positive")
    Integer reorderUpTo
) {
    @AssertTrue(message = "Reorder up to must be greater than reorder point")
    public boolean isReorderUpToAboveReorderPoint() {
        return reorderPoint == null || reorderUpTo == null || reorderUpTo > reorderPoint;
    }
}
