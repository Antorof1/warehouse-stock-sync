package com.github.antorof1.simulator.web;

import com.github.antorof1.simulator.config.SimulatorProperties;
import com.github.antorof1.simulator.scenario.BurstScenario;
import com.github.antorof1.simulator.scenario.ScenarioRun;
import com.github.antorof1.simulator.scenario.ScenarioRunner;
import com.github.antorof1.simulator.supplier.SupplierSimulator;
import com.github.antorof1.simulator.traffic.TrafficGenerator;
import com.github.antorof1.simulator.web.dto.PanelConfig;
import com.github.antorof1.simulator.web.dto.RunScenarioRequest;
import com.github.antorof1.simulator.web.dto.SupplierSettings;
import com.github.antorof1.simulator.web.dto.TrafficSettings;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/simulator")
public class SimulatorController {
    private final TrafficGenerator traffic;
    private final SupplierSimulator supplier;
    private final ScenarioRunner scenarios;
    private final SnapshotAssembler assembler;
    private final LiveUpdates liveUpdates;
    private final SimulatorProperties properties;

    public SimulatorController(TrafficGenerator traffic, SupplierSimulator supplier, ScenarioRunner scenarios,
                               SnapshotAssembler assembler, LiveUpdates liveUpdates,
                               SimulatorProperties properties) {
        this.traffic = traffic;
        this.supplier = supplier;
        this.scenarios = scenarios;
        this.assembler = assembler;
        this.liveUpdates = liveUpdates;
        this.properties = properties;
    }

    @GetMapping("/config")
    public PanelConfig config() {
        return PanelConfig.from(properties, scenarios.scenarios());
    }

    @GetMapping("/snapshot")
    public Snapshot snapshot() {
        return assembler.snapshot();
    }

    @GetMapping("/stream")
    public SseEmitter stream() {
        return liveUpdates.subscribe();
    }

    @PostMapping("/traffic/start")
    public TrafficGenerator.TrafficStatus startTraffic() {
        traffic.start();
        return traffic.status();
    }

    @PostMapping("/traffic/stop")
    public TrafficGenerator.TrafficStatus stopTraffic() {
        traffic.stop();
        return traffic.status();
    }

    @PutMapping("/traffic")
    public TrafficGenerator.TrafficStatus updateTraffic(@RequestBody @Valid TrafficSettings settings) {
        traffic.setRate(settings.rate());
        return traffic.status();
    }

    @PutMapping("/supplier")
    public SupplierSimulator.SupplierStatus updateSupplier(@RequestBody @Valid SupplierSettings settings) {
        supplier.configure(settings.enabled(), Duration.ofSeconds(settings.leadTimeSeconds()));
        return supplier.status();
    }

    @PostMapping("/scenarios/{id}")
    public ResponseEntity<ScenarioRun.Status> runScenario(
        @PathVariable("id") String id,
        @RequestBody(required = false) @Valid RunScenarioRequest request
    ) {
        Map<String, Integer> options = new HashMap<>();
        if (request != null && request.size() != null) {
            options.put(BurstScenario.SIZE_OPTION, request.size());
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(scenarios.start(id, options));
    }

    @DeleteMapping("/scenarios/current")
    public ResponseEntity<Void> stopScenario() {
        return scenarios.stop() ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
