package com.github.antorof1.simulator.scenario;

import com.github.antorof1.simulator.client.WarehouseClient;
import com.github.antorof1.simulator.config.SimulatorProperties;
import com.github.antorof1.simulator.exception.ScenarioBusyException;
import com.github.antorof1.simulator.exception.UnknownScenarioException;
import com.github.antorof1.simulator.state.SimulationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
public class ScenarioRunner implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(ScenarioRunner.class);

    private final Map<String, Scenario> scenarios = new LinkedHashMap<>();
    private final WarehouseClient warehouseClient;
    private final SimulationState state;
    private final Clock clock;
    private final SimulatorProperties.Scenarios settings;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private ScenarioRun current;
    private Future<?> currentTask;

    public ScenarioRunner(List<Scenario> scenarios, WarehouseClient warehouseClient, SimulationState state,
                          Clock clock, SimulatorProperties properties) {
        scenarios.forEach(scenario -> this.scenarios.put(scenario.id(), scenario));
        this.warehouseClient = warehouseClient;
        this.state = state;
        this.clock = clock;
        this.settings = properties.scenarios();
    }

    public List<Scenario> scenarios() {
        return List.copyOf(scenarios.values());
    }

    public synchronized ScenarioRun.Status start(String id, Map<String, Integer> options) {
        Scenario scenario = scenarios.get(id);
        if (scenario == null) {
            throw new UnknownScenarioException(id);
        }
        if (current != null && current.isRunning()) {
            throw new ScenarioBusyException(current.status().name());
        }

        ScenarioRun run = new ScenarioRun(scenario, clock);
        ScenarioContext context = new ScenarioContext(run, warehouseClient, state, clock, settings, options);

        current = run;
        currentTask = executor.submit(() -> execute(scenario, run, context));
        log.info("Started scenario {}", id);

        return run.status();
    }

    public synchronized boolean stop() {
        if (current == null || !current.isRunning()) return false;

        current.finish(ScenarioRun.State.CANCELLED, "Stopped by the presenter");
        currentTask.cancel(true);

        return true;
    }

    public synchronized Optional<ScenarioRun.Status> status() {
        return Optional.ofNullable(current).map(ScenarioRun::status);
    }

    private void execute(Scenario scenario, ScenarioRun run, ScenarioContext context) {
        try {
            scenario.run(context);
            run.finish(ScenarioRun.State.SUCCEEDED, "Scenario complete");
        } catch (InterruptedException e) {
            run.finish(ScenarioRun.State.CANCELLED, null);
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            run.finish(ScenarioRun.State.FAILED, e.getMessage());
            log.warn("Scenario {} failed: {}", scenario.id(), e.getMessage());
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
