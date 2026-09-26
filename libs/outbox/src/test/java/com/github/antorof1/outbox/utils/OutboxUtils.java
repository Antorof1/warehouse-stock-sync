package com.github.antorof1.outbox.utils;

import com.github.antorof1.outbox.OutboxEvent;
import com.github.antorof1.outbox.OutboxProperties;
import jakarta.persistence.EntityManager;
import org.jspecify.annotations.Nullable;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class OutboxUtils {
    public static final String TOPIC = "outbox-test-events";
    public static final String TYPE = "outbox.test.event";
    public static final String TYPE_MAPPING = TYPE + ":com.github.antorof1.outbox.utils.TestEvent";
    public static final String KEY = "KEY-1001";
    public static final String NAME = "widget";
    public static final int AMOUNT = 5;
    public static final Instant OCCURRED_AT = Instant.parse("2026-09-20T10:15:30.123456Z");
    public static final String PAYLOAD = "{\"name\": \"" + NAME + "\"}";

    private OutboxUtils() {
    }

    public static TestEvent event() {
        return event(UUID.randomUUID());
    }

    public static TestEvent event(UUID id) {
        return new TestEvent(id, NAME, AMOUNT, OCCURRED_AT);
    }

    public static OutboxEvent outboxEvent(UUID id, String key) {
        return new OutboxEvent(id, TOPIC, key, TYPE, PAYLOAD);
    }

    public static OutboxProperties properties(int batchSize, Duration retention) {
        return new OutboxProperties(true, Duration.ofMillis(500), batchSize, retention, Duration.ofHours(1));
    }

    public static String uniqueKey() {
        return "KEY-" + UUID.randomUUID();
    }

    public static UUID insert(EntityManager entityManager, @Nullable Instant publishedAt, Instant createdAt) {
        UUID id = UUID.randomUUID();

        entityManager.createNativeQuery("""
                INSERT INTO outbox_events (id, topic, key, type, payload, published_at, created_at)
                VALUES (:id, :topic, :key, :type, CAST(:payload AS jsonb), :publishedAt, :createdAt)
                """)
            .setParameter("id", id)
            .setParameter("topic", TOPIC)
            .setParameter("key", uniqueKey())
            .setParameter("type", TYPE)
            .setParameter("payload", PAYLOAD)
            .setParameter("publishedAt", publishedAt == null ? null : Timestamp.from(publishedAt))
            .setParameter("createdAt", Timestamp.from(createdAt))
            .executeUpdate();

        return id;
    }

    public static boolean exists(EntityManager entityManager, UUID id) {
        return !entityManager.createNativeQuery("SELECT 1 FROM outbox_events WHERE id = :id")
            .setParameter("id", id)
            .getResultList()
            .isEmpty();
    }

    public static @Nullable Instant publishedAt(EntityManager entityManager, UUID id) {
        Object value = entityManager.createNativeQuery("SELECT published_at FROM outbox_events WHERE id = :id")
            .setParameter("id", id)
            .getSingleResult();

        return switch (value) {
            case null -> null;
            case Instant instant -> instant;
            case Timestamp timestamp -> timestamp.toInstant();
            case java.time.OffsetDateTime offset -> offset.toInstant();
            default -> throw new IllegalStateException("Unexpected published_at type " + value.getClass());
        };
    }
}
