package com.github.antorof1.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.outbox")
public record OutboxProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("PT0.5S") Duration pollInterval,
    @DefaultValue("100") int batchSize,
    @DefaultValue("P7D") Duration retention,
    @DefaultValue("PT1H") Duration sweepInterval
) {
}
