package com.github.antorof1.simulator.traffic;

import com.github.antorof1.simulator.client.Movement;
import com.github.antorof1.simulator.client.WarehouseClient;
import com.github.antorof1.simulator.config.SimulatorProperties;
import com.github.antorof1.simulator.state.SimulationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;

@Component
public class TrafficGenerator implements DisposableBean {
    public static final double MIN_RATE = 0.2;
    public static final double MAX_RATE = 50;

    private static final Logger log = LoggerFactory.getLogger(TrafficGenerator.class);

    private final MovementPlanner planner;
    private final CatalogSeeder seeder;
    private final WarehouseClient warehouseClient;
    private final SimulationState state;
    private final Clock clock;
    private final RandomGenerator random = new Random();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
        Thread.ofPlatform().name("traffic").daemon().factory()
    );

    private final InFlightDecreases inFlight = new InFlightDecreases();
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private volatile double rate;
    private volatile boolean running;
    private volatile String lastError;
    private volatile Instant lastErrorAt;
    private long generation;
    private ScheduledFuture<?> nextTick;

    public TrafficGenerator(MovementPlanner planner, CatalogSeeder seeder, WarehouseClient warehouseClient,
                            SimulationState state, Clock clock, SimulatorProperties properties) {
        this.planner = planner;
        this.seeder = seeder;
        this.warehouseClient = warehouseClient;
        this.state = state;
        this.clock = clock;
        this.rate = clamp(properties.traffic().rate());
    }

    public synchronized void start() {
        if (running) return;

        running = true;
        scheduleNextTick(++generation, 0);
        log.info("Traffic started at {} movements/s", rate);
    }

    public synchronized void stop() {
        if (!running) return;

        running = false;
        if (nextTick != null) {
            nextTick.cancel(false);
        }
        log.info("Traffic stopped");
    }

    public void setRate(double rate) {
        this.rate = clamp(rate);
    }

    public TrafficStatus status() {
        return new TrafficStatus(running, rate, seeder.isSeeded(), sent.get(), failed.get(), lastError, lastErrorAt);
    }

    private void tick(long tickGeneration) {
        try {
            if (!seeder.isSeeded()) {
                seeder.seed();
                return;
            }

            List<Movement> movements = planner.next(random, inFlight.adjust(state.levels(), clock.instant()));
            for (Movement movement : movements) {
                warehouseClient.record(movement);
                inFlight.record(movement, clock.instant());
                sent.incrementAndGet();
            }
        } catch (RestClientException e) {
            failed.incrementAndGet();
            lastError = e.getMessage();
            lastErrorAt = clock.instant();
            log.warn("Traffic tick failed: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.error("Traffic tick failed", e);
        } finally {
            scheduleNextTick(tickGeneration, Math.round(1000 / rate));
        }
    }

    private synchronized void scheduleNextTick(long tickGeneration, long delayMillis) {
        if (running && tickGeneration == generation) {
            nextTick = executor.schedule(() -> tick(tickGeneration), delayMillis, TimeUnit.MILLISECONDS);
        }
    }

    private static double clamp(double rate) {
        return Math.clamp(rate, MIN_RATE, MAX_RATE);
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    public record TrafficStatus(
        boolean running,
        double rate,
        boolean seeded,
        long sent,
        long failed,
        String lastError,
        Instant lastErrorAt
    ) {
    }
}
