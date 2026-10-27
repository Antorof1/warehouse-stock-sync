package com.github.antorof1.idempotency.utils;

import java.util.UUID;

public final class IdempotencyUtils {
    public static final String TOPIC = "idempotency.test.events";
    public static final String OTHER_TOPIC = "idempotency.test.other-events";

    private IdempotencyUtils() {
    }

    public static UUID newEventId() {
        return UUID.randomUUID();
    }
}
