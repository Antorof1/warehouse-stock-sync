package com.github.antorof1.reorder.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
    @Modifying
    @Query(value = "INSERT INTO processed_events (id) VALUES (:id) ON CONFLICT DO NOTHING",
        nativeQuery = true)
    int insertIgnoreConflict(@Param("id") UUID id);
}
