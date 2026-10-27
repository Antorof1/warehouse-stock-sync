package com.github.antorof1.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.util.concurrent.TimeUnit;

public class OutboxMetrics implements MeterBinder {
    static final String PENDING = "outbox.events.pending";
    static final String OLDEST_PENDING_AGE = "outbox.events.oldest.pending.age";

    private final OutboxEventRepository outboxEventRepository;

    public OutboxMetrics(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(PENDING, outboxEventRepository, OutboxEventRepository::countPending)
            .description("Outbox events recorded but not yet published to Kafka")
            .register(registry);

        TimeGauge.builder(OLDEST_PENDING_AGE, outboxEventRepository, TimeUnit.SECONDS, OutboxMetrics::oldestPendingAge)
            .description("Age of the oldest outbox event still waiting to be published, 0 when none is pending")
            .register(registry);
    }

    private static double oldestPendingAge(OutboxEventRepository outboxEventRepository) {
        Double age = outboxEventRepository.oldestPendingAgeSeconds();

        return age == null ? 0 : age;
    }
}
