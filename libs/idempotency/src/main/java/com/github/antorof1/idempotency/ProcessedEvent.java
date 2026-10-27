package com.github.antorof1.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {
    @EmbeddedId
    private ProcessedEventId id;

    @Column(nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(ProcessedEventId id) {
        this.id = id;
    }

    public ProcessedEventId getId() {
        return id;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
