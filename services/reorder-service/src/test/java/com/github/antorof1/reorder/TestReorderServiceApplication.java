package com.github.antorof1.reorder;

import org.springframework.boot.SpringApplication;

public class TestReorderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(ReorderServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
