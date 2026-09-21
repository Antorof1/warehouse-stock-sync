package com.github.antorof1.reorder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReorderServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(ReorderServiceApplication.class, args);
	}
}
