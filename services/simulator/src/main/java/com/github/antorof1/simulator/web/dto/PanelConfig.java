package com.github.antorof1.simulator.web.dto;

import com.github.antorof1.simulator.config.SimulatorProperties;
import com.github.antorof1.simulator.scenario.BurstScenario;
import com.github.antorof1.simulator.scenario.Scenario;
import com.github.antorof1.simulator.traffic.TrafficGenerator;

import java.util.List;

public record PanelConfig(
    List<SimulatorProperties.Link> links,
    List<ScenarioInfo> scenarios,
    int defaultBurstSize,
    int maxBurstSize,
    double minRate,
    double maxRate
) {
    public static PanelConfig from(SimulatorProperties properties, List<Scenario> scenarios) {
        return new PanelConfig(
            properties.links(),
            scenarios.stream().map(ScenarioInfo::from).toList(),
            properties.scenarios().burstSize(),
            BurstScenario.MAX_SIZE,
            TrafficGenerator.MIN_RATE,
            TrafficGenerator.MAX_RATE
        );
    }

    public record ScenarioInfo(String id, String name, String description) {
        static ScenarioInfo from(Scenario scenario) {
            return new ScenarioInfo(scenario.id(), scenario.name(), scenario.description());
        }
    }
}
