package com.github.antorof1.idempotency;

import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public record ProcessedEventId(
    UUID eventId,
    String eventTopic
) {
}
