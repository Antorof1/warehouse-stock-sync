package com.github.antorof1.simulator.web.dto;

import com.github.antorof1.simulator.scenario.BurstScenario;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RunScenarioRequest(
    @Min(value = 2, message = "Size must be at least 2")
    @Max(value = BurstScenario.MAX_SIZE, message = "Size can't exceed " + BurstScenario.MAX_SIZE)
    Integer size
) {
}
