package com.github.antorof1.outbox.utils;

import java.time.Instant;
import java.util.UUID;

public record TestEvent(UUID id, String name, int amount, Instant occurredAt) {
}
