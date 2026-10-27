package com.github.antorof1.kafkaretry;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("app.kafka.retry")
public record KafkaRetryProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("4") int maxRetries,
    @DefaultValue("PT1S") Duration initialInterval,
    @DefaultValue("2.0") double multiplier,
    @DefaultValue("PT10S") Duration maxInterval,
    @DefaultValue List<Class<? extends Exception>> notRetryableExceptions
) {
}
