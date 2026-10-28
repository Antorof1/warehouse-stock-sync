package com.github.antorof1.simulator.scenario;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.simulator.client.Movement;
import com.github.antorof1.simulator.client.WarehouseClient;
import com.github.antorof1.simulator.config.SimulatorProperties;
import com.github.antorof1.simulator.exception.ScenarioFailedException;
import com.github.antorof1.simulator.state.SimulationState;
import com.github.antorof1.simulator.state.StockKey;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

public class ScenarioContext {
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    private final ScenarioRun run;
    private final WarehouseClient warehouseClient;
    private final SimulationState state;
    private final Clock clock;
    private final SimulatorProperties.Scenarios settings;
    private final Map<String, Integer> options;

    ScenarioContext(ScenarioRun run, WarehouseClient warehouseClient, SimulationState state, Clock clock,
                    SimulatorProperties.Scenarios settings, Map<String, Integer> options) {
        this.run = run;
        this.warehouseClient = warehouseClient;
        this.state = state;
        this.clock = clock;
        this.settings = settings;
        this.options = Map.copyOf(options);
    }

    public void step(String text) {
        run.log(ScenarioRun.LogLine.Kind.STEP, text);
    }

    public void info(String text) {
        run.log(ScenarioRun.LogLine.Kind.INFO, text);
    }

    public void pause() throws InterruptedException {
        Thread.sleep(settings.stepDelay());
    }

    public void await(String description, BooleanSupplier condition) throws InterruptedException {
        await(description, condition, settings.awaitTimeout());
    }

    public void await(String description, BooleanSupplier condition, Duration timeout) throws InterruptedException {
        Instant deadline = clock.instant().plus(timeout);

        while (!condition.getAsBoolean()) {
            if (clock.instant().isAfter(deadline)) {
                throw new ScenarioFailedException(
                    "Timed out after %ds waiting for %s".formatted(timeout.toSeconds(), description)
                );
            }
            Thread.sleep(POLL_INTERVAL);
        }
    }

    public void awaitLevel(StockKey key, int level) throws InterruptedException {
        await("stock of %s to reach %d".formatted(key, level),
            () -> state.level(key).filter(current -> current == level).isPresent());
        info("Stock of %s is now %d".formatted(key, level));
    }

    public void record(StockKey key, int quantityDelta, MovementReason reason) {
        warehouseClient.record(Movement.of(key, quantityDelta, reason));
    }

    public StockKey newStockKey(String label) {
        String suffix = Long.toString(clock.instant().getEpochSecond(), 36).toUpperCase(Locale.ROOT);

        return new StockKey(settings.warehouseId(), settings.skuPrefix() + label + "-" + suffix);
    }

    public int option(String name, int defaultValue) {
        return options.getOrDefault(name, defaultValue);
    }

    public SimulationState state() {
        return state;
    }

    public SimulatorProperties.Scenarios settings() {
        return settings;
    }

    public Clock clock() {
        return clock;
    }
}
