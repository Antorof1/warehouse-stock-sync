package com.github.antorof1.simulator.scenario;

public interface Scenario {
    String id();

    String name();

    String description();

    void run(ScenarioContext context) throws InterruptedException;
}
