package com.github.antorof1.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.github.antorof1.idempotency.utils.IdempotencyUtils.exists;
import static com.github.antorof1.idempotency.utils.IdempotencyUtils.insertProcessedAt;
import static org.assertj.core.api.Assertions.assertThat;

@IdempotencyIntegrationTest
class ProcessedEventPrunerIntegrationTest {
    @Autowired
    private ProcessedEventPruner processedEventPruner;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void prunesEventsProcessedLongerAgoThanTheDefaultRetention() {
        Instant now = Instant.now();
        UUID expired = insertProcessedAt(jdbcTemplate, now.minus(Duration.ofDays(15)));
        UUID retained = insertProcessedAt(jdbcTemplate, now.minus(Duration.ofDays(13)));

        processedEventPruner.run();

        assertThat(exists(jdbcTemplate, expired)).isFalse();
        assertThat(exists(jdbcTemplate, retained)).isTrue();
    }
}
