package com.github.antorof1.warehouse.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Transactional
    @Query(value = """
        SELECT * FROM outbox_events
        WHERE published_at IS NULL
        ORDER BY created_at
        LIMIT :limit
        """, nativeQuery = true)
    List<OutboxEvent> findPending(@Param("limit") int limit);

    @Modifying
    @Transactional
    @Query(value = "UPDATE outbox_events SET published_at = now() WHERE id IN (:ids)", nativeQuery = true)
    void markPublished(@Param("ids") List<UUID> ids);

    @Modifying
    @Query(value = "DELETE FROM OutboxEvent e WHERE e.publishedAt < :cutoff")
    void deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
