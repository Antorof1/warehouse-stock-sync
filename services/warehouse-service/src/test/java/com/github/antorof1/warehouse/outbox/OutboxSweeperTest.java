package com.github.antorof1.warehouse.outbox;

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
class OutboxSweeperTest {
    private static final Duration RETENTION = Duration.ofDays(7);

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Captor
    private ArgumentCaptor<Instant> cutoffCaptor;

    @Test
    void deletesEventsPublishedBeforeTheRetentionWindow() {
        OutboxSweeper sweeper = new OutboxSweeper(outboxEventRepository, RETENTION);

        Instant before = Instant.now();
        sweeper.run();
        Instant after = Instant.now();

        verify(outboxEventRepository).deletePublishedBefore(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isBetween(before.minus(RETENTION), after.minus(RETENTION));
    }
}
