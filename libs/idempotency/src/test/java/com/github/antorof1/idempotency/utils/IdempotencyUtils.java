package com.github.antorof1.idempotency.utils;

import com.github.antorof1.idempotency.IdempotencyProperties;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class IdempotencyUtils {
    public static final String TOPIC = "idempotency.test.events";
    public static final String OTHER_TOPIC = "idempotency.test.other-events";

    private IdempotencyUtils() {
    }

    public static UUID newEventId() {
        return UUID.randomUUID();
    }

    public static IdempotencyProperties properties(Duration retention) {
        return new IdempotencyProperties(new IdempotencyProperties.Pruning(true, retention, Duration.ofHours(1)));
    }

    public static UUID insertProcessedAt(JdbcTemplate jdbcTemplate, Instant processedAt) {
        UUID id = newEventId();
        jdbcTemplate.update(
            "INSERT INTO processed_events (event_id, event_topic, processed_at) VALUES (?, ?, ?)",
            id, TOPIC, Timestamp.from(processedAt)
        );

        return id;
    }

    public static boolean exists(JdbcTemplate jdbcTemplate, UUID id) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM processed_events WHERE event_id = ?", Long.class, id
        );

        return count != null && count > 0;
    }
}
