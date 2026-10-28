package com.github.antorof1.simulator.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SupplierSettings(
    @NotNull(message = "Enabled is required")
    Boolean enabled,

    @NotNull(message = "Lead time is required")
    @Min(value = 1, message = "Lead time must be at least 1 second")
    @Max(value = 300, message = "Lead time can't exceed 300 seconds")
    Integer leadTimeSeconds
) {
}
