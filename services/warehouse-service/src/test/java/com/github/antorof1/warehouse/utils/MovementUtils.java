package com.github.antorof1.warehouse.utils;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.warehouse.movement.Movement;

import java.time.Instant;
import java.util.UUID;

public final class MovementUtils {
    public static final String SKU = "SKU-1001";
    public static final String WAREHOUSE_ID = "WH-WROCLAW";
    public static final int QUANTITY_DELTA = 5;
    public static final MovementReason REASON = MovementReason.RECEIPT;
    public static final Instant OCCURRED_AT = Instant.parse("2026-09-20T10:15:30.123456Z");

    public static final int MAX_FIELD_LENGTH = 64;

    private MovementUtils() {
    }

    public static Movement movement() {
        return new Movement(SKU, WAREHOUSE_ID, QUANTITY_DELTA, REASON, OCCURRED_AT);
    }

    public static Movement movement(String sku) {
        return new Movement(sku, WAREHOUSE_ID, QUANTITY_DELTA, REASON, OCCURRED_AT);
    }

    public static Movement savedMovement(UUID id) {
        Movement movement = movement();
        movement.setId(id);
        return movement;
    }

    public static MovementEvent event() {
        return new MovementEvent(UUID.randomUUID(), SKU, WAREHOUSE_ID, QUANTITY_DELTA, REASON, OCCURRED_AT);
    }

    public static String uniqueSku() {
        return "SKU-" + UUID.randomUUID();
    }

    public static String ofLength(char character, int length) {
        return String.valueOf(character).repeat(length);
    }
}
