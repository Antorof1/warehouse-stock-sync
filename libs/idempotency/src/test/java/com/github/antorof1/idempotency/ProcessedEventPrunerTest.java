package com.github.antorof1.idempotency;

import com.github.antorof1.idempotency.utils.IdempotencyUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProcessedEventPrunerTest {
    private static final Duration RETENTION = Duration.ofDays(3);

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Captor
    private ArgumentCaptor<Instant> cutoffCaptor;

    @Test
    void deletesEventsProcessedBeforeTheRetentionWindow() {
        ProcessedEventPruner pruner = new ProcessedEventPruner(processedEventRepository, IdempotencyUtils.properties(RETENTION));

        Instant before = Instant.now();
        pruner.run();
        Instant after = Instant.now();

        verify(processedEventRepository).deleteProcessedBefore(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isBetween(before.minus(RETENTION), after.minus(RETENTION));
    }
}
