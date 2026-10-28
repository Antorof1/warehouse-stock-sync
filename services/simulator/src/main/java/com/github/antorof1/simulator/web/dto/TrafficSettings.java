package com.github.antorof1.simulator.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record TrafficSettings(
    @NotNull(message = "Rate is required")
    @DecimalMin(value = "0.2", message = "Rate must be at least 0.2 movements per second")
    @DecimalMax(value = "50", message = "Rate can't exceed 50 movements per second")
    Double rate
) {
}
