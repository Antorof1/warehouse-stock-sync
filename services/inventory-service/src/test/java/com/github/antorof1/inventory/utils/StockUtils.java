package com.github.antorof1.inventory.utils;

import com.fasterxml.uuid.Generators;
import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.inventory.stock.Stock;
import com.github.antorof1.inventory.stock.StockId;

import java.time.Instant;
import java.util.UUID;

public final class StockUtils {
    public static final String SKU = "SKU-2001";
    public static final String WAREHOUSE_ID = "WH-WROCLAW";
    public static final MovementReason REASON = MovementReason.RECEIPT;
    public static final Instant OCCURRED_AT = Instant.parse("2026-09-20T10:15:30.123456Z");

    public static final int MAX_FIELD_LENGTH = 64;

    private StockUtils() {
    }

    public static StockId stockId() {
        return new StockId(SKU, WAREHOUSE_ID);
    }

    public static StockId stockId(String sku) {
        return new StockId(sku, WAREHOUSE_ID);
    }

    public static Stock stock(int quantity) {
        return new Stock(stockId(), quantity);
    }

    public static Stock stock(String sku, int quantity) {
        return new Stock(stockId(sku), quantity);
    }

    public static MovementEvent movementEvent(String sku, int quantityDelta) {
        return movementEvent(newEventId(), sku, quantityDelta, REASON);
    }

    public static MovementEvent movementEvent(String sku, int quantityDelta, MovementReason reason) {
        return movementEvent(newEventId(), sku, quantityDelta, reason);
    }

    public static MovementEvent movementEvent(UUID id, String sku, int quantityDelta, MovementReason reason) {
        return new MovementEvent(id, sku, WAREHOUSE_ID, quantityDelta, reason, OCCURRED_AT);
    }

    public static UUID newEventId() {
        return Generators.timeBasedEpochGenerator().generate();
    }

    public static String uniqueSku() {
        return "SKU-" + UUID.randomUUID();
    }

    public static String ofLength(char character, int length) {
        return String.valueOf(character).repeat(length);
    }
}
