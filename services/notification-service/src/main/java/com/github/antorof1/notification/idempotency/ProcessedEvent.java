package com.github.antorof1.notification.idempotency;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

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
}
