package com.github.antorof1.reorder.idempotency;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {
    @Id
    private UUID id;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}
