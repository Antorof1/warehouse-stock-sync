package com.github.antorof1.simulator.scenario;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ScenarioRun {
    static final int MAX_LOG_LINES = 200;

    private final Scenario scenario;
    private final Clock clock;
    private final Instant startedAt;
    private final List<LogLine> log = new ArrayList<>();
    private State state = State.RUNNING;
    private Instant finishedAt;

    ScenarioRun(Scenario scenario, Clock clock) {
        this.scenario = scenario;
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    public synchronized boolean isRunning() {
        return state == State.RUNNING;
    }

    synchronized void log(LogLine.Kind kind, String text) {
        log.add(new LogLine(clock.instant(), kind, text));
        if (log.size() > MAX_LOG_LINES) {
            log.removeFirst();
        }
    }

    synchronized void finish(State state, String message) {
        if (this.state != State.RUNNING) return;

        if (message != null) {
            log(state == State.SUCCEEDED ? LogLine.Kind.SUCCESS : LogLine.Kind.ERROR, message);
        }
        this.state = state;
        this.finishedAt = clock.instant();
    }

    public synchronized Status status() {
        return new Status(scenario.id(), scenario.name(), state, startedAt, finishedAt, List.copyOf(log));
    }

    public enum State {
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    public record LogLine(Instant at, Kind kind, String text) {
        public enum Kind {
            STEP,
            INFO,
            SUCCESS,
            ERROR
        }
    }

    public record Status(
        String id,
        String name,
        State state,
        Instant startedAt,
        Instant finishedAt,
        List<LogLine> log
    ) {
    }
}
