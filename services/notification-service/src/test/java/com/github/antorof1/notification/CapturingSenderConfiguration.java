package com.github.antorof1.notification;

import com.github.antorof1.notification.utils.CapturingSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
class CapturingSenderConfiguration {
	@Bean
	@Primary
	CapturingSender capturingSender() {
		return new CapturingSender();
	}
}
