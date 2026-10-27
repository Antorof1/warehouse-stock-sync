package com.github.antorof1.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.idempotency")
public record IdempotencyProperties(
    @DefaultValue Pruning pruning
) {
    public record Pruning(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("P14D") Duration retention,
        @DefaultValue("PT1H") Duration interval
    ) {
    }
}
