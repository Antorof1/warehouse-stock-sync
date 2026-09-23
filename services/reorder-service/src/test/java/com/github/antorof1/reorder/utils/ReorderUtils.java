package com.github.antorof1.reorder.utils;

import com.fasterxml.uuid.Generators;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.reorder.rule.ReorderRule;
import com.github.antorof1.reorder.rule.ReorderRuleId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class ReorderUtils {
    public static final String SKU = "SKU-2001";
    public static final String WAREHOUSE_ID = "WH-WROCLAW";
    public static final Instant OCCURRED_AT = Instant.parse("2026-09-20T10:15:30.123456Z");

    public static final int REORDER_POINT = 10;
    public static final int REORDER_UP_TO = 50;

    public static final Duration PAST_TTL = Duration.ofDays(8);

    private ReorderUtils() {
    }

    public static ReorderRuleId ruleId(String sku) {
        return new ReorderRuleId(sku, WAREHOUSE_ID);
    }

    public static ReorderRule rule(String sku) {
        return rule(sku, REORDER_POINT, REORDER_UP_TO);
    }

    public static ReorderRule rule(String sku, int reorderPoint, int reorderUpTo) {
        return new ReorderRule(ruleId(sku), reorderPoint, reorderUpTo);
    }

    public static StockLevelEvent stockLevelEvent(String sku, int previousLevel, int newLevel, MovementReason reason) {
        return stockLevelEvent(newEventId(), sku, previousLevel, newLevel, reason);
    }

    public static StockLevelEvent stockLevelEvent(UUID id, String sku, int previousLevel, int newLevel,
                                                  MovementReason reason) {
        return new StockLevelEvent(id, sku, WAREHOUSE_ID, newLevel, previousLevel, reason, OCCURRED_AT);
    }

    public static void ageOrder(JdbcTemplate jdbcTemplate, UUID orderId, Duration age) {
        jdbcTemplate.update(
            "UPDATE purchase_orders SET created_at = ? WHERE id = ?",
            Timestamp.from(Instant.now().minus(age)), orderId
        );
    }

    public static UUID newEventId() {
        return Generators.timeBasedEpochGenerator().generate();
    }

    public static String uniqueSku() {
        return "SKU-" + UUID.randomUUID();
    }
}
