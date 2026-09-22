package com.github.antorof1.notification.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {
    @Modifying
    @Query(value = "INSERT INTO processed_events (event_id, event_topic) VALUES (:id, :topic) ON CONFLICT DO NOTHING",
        nativeQuery = true)
    int insertIgnoreConflict(@Param("id") UUID id, @Param("topic") String topic);
}
