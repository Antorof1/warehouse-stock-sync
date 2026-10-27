package com.github.antorof1.idempotency;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {
    @EmbeddedId
    private ProcessedEventId id;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(ProcessedEventId id) {
        this.id = id;
    }

    public ProcessedEventId getId() {
        return id;
    }
}
