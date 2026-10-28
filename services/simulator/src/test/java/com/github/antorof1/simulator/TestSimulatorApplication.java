package com.github.antorof1.simulator;

import org.springframework.boot.SpringApplication;

public class TestSimulatorApplication {
	public static void main(String[] args) {
		SpringApplication.from(SimulatorApplication::main).with(TestcontainersConfiguration.class).run(args);
	}
}
