package com.github.antorof1.outbox;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OutboxMetricsTest {
    @Mock
    private OutboxEventRepository outboxEventRepository;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @BeforeEach
    void setUp() {
        new OutboxMetrics(outboxEventRepository).bindTo(meterRegistry);
    }

    @Test
    void reportsHowManyEventsArePending() {
        given(outboxEventRepository.countPending()).willReturn(7L);

        assertThat(meterRegistry.get(OutboxMetrics.PENDING).gauge().value()).isEqualTo(7);
    }

    @Test
    void reportsTheAgeOfTheOldestPendingEvent() {
        given(outboxEventRepository.oldestPendingAgeSeconds()).willReturn(42.5);

        assertThat(meterRegistry.get(OutboxMetrics.OLDEST_PENDING_AGE).timeGauge().value(TimeUnit.SECONDS))
            .isEqualTo(42.5);
    }

    @Test
    void reportsZeroAgeWhenNothingIsPending() {
        given(outboxEventRepository.oldestPendingAgeSeconds()).willReturn(null);

        assertThat(meterRegistry.get(OutboxMetrics.OLDEST_PENDING_AGE).timeGauge().value(TimeUnit.SECONDS))
            .isZero();
    }
}
