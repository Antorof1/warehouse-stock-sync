package com.github.antorof1.notification.utils;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderCompletedEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderExpiredEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderPlacedEvent;

import java.time.Instant;
import java.util.UUID;

public final class NotificationUtils {
    public static final String SKU = "SKU-2001";
    public static final String WAREHOUSE_ID = "WH-WROCLAW";
    public static final Instant OCCURRED_AT = Instant.parse("2026-09-20T10:15:30.123456Z");
    public static final Instant PLACED_AT = Instant.parse("2026-09-18T08:00:00Z");

    public static final int BULK_THRESHOLD = 500;

    private NotificationUtils() {
    }

    public static MovementEvent movementEvent(String sku, int quantityDelta, MovementReason reason) {
        return new MovementEvent(newEventId(), sku, WAREHOUSE_ID, quantityDelta, reason, OCCURRED_AT);
    }

    public static StockLevelEvent stockLevelEvent(String sku, int previousLevel, int newLevel, MovementReason reason) {
        return new StockLevelEvent(newEventId(), sku, WAREHOUSE_ID, newLevel, previousLevel, reason, OCCURRED_AT);
    }

    public static PurchaseOrderPlacedEvent placedEvent(String sku, int quantity) {
        return new PurchaseOrderPlacedEvent(newEventId(), sku, WAREHOUSE_ID, quantity, OCCURRED_AT);
    }

    public static PurchaseOrderCompletedEvent completedEvent(String sku, int orderedQuantity) {
        return new PurchaseOrderCompletedEvent(newEventId(), sku, WAREHOUSE_ID, orderedQuantity, PLACED_AT, OCCURRED_AT);
    }

    public static PurchaseOrderExpiredEvent expiredEvent(String sku, int orderedQuantity, int receivedQuantity) {
        return new PurchaseOrderExpiredEvent(
            newEventId(), sku, WAREHOUSE_ID, orderedQuantity, receivedQuantity, OCCURRED_AT
        );
    }

    public static UUID newEventId() {
        return UUID.randomUUID();
    }

    public static String uniqueSku() {
        return "SKU-" + UUID.randomUUID();
    }
}
