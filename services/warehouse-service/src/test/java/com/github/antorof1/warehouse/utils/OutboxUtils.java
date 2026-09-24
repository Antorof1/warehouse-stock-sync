package com.github.antorof1.warehouse.utils;

import com.github.antorof1.events.Topics;
import com.github.antorof1.warehouse.outbox.OutboxEvent;
import jakarta.persistence.EntityManager;
import org.jspecify.annotations.Nullable;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

public final class OutboxUtils {
    public static final String TYPE = MovementTopicConsumer.MOVEMENT_RECORDED_TYPE;
    public static final String PAYLOAD = "{\"sku\": \"" + MovementUtils.SKU + "\"}";

    private OutboxUtils() {
    }

    public static OutboxEvent outboxEvent(UUID id, String key) {
        return new OutboxEvent(id, Topics.MOVEMENT_EVENTS, key, TYPE, PAYLOAD);
    }

    public static UUID insert(EntityManager entityManager, @Nullable Instant publishedAt, Instant createdAt) {
        UUID id = UUID.randomUUID();

        entityManager.createNativeQuery("""
                INSERT INTO outbox_events (id, topic, key, type, payload, published_at, created_at)
                VALUES (:id, :topic, :key, :type, CAST(:payload AS jsonb), :publishedAt, :createdAt)
                """)
            .setParameter("id", id)
            .setParameter("topic", Topics.MOVEMENT_EVENTS)
            .setParameter("key", MovementUtils.uniqueSku().substring(0, 40))
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
