package com.github.antorof1.simulator.utils;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderCompletedEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderExpiredEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderPlacedEvent;
import com.github.antorof1.simulator.config.SimulatorProperties;
import com.github.antorof1.simulator.config.SimulatorProperties.CatalogSku;
import com.github.antorof1.simulator.state.StockKey;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SimulatorUtils {
    public static final String WH_1 = "WH-1";
    public static final String WH_2 = "WH-2";
    public static final String BOLTS = "SKU-1001";
    public static final String DRILLS = "SKU-2001";
    public static final int BOLTS_REORDER_POINT = 40;
    public static final int BOLTS_REORDER_UP_TO = 200;

    public static final StockKey BOLTS_WH_1 = new StockKey(WH_1, BOLTS);
    public static final StockKey BOLTS_WH_2 = new StockKey(WH_2, BOLTS);
    public static final StockKey DRILLS_WH_1 = new StockKey(WH_1, DRILLS);

    public static final Instant OCCURRED_AT = Instant.parse("2026-10-20T10:15:30Z");

    private SimulatorUtils() {
    }

    public static SimulatorProperties properties() {
        return properties(Duration.ofSeconds(8), true);
    }

    public static SimulatorProperties properties(Duration leadTime, boolean partialShipments) {
        URI local = URI.create("http://localhost");

        return new SimulatorProperties(
            new SimulatorProperties.Services(local, local, local, local),
            List.of(new SimulatorProperties.Link("Grafana", "http://localhost:3000")),
            new SimulatorProperties.Catalog(
                List.of(WH_1, WH_2),
                List.of(
                    new CatalogSku(BOLTS, "Hex bolt M8", BOLTS_REORDER_POINT, BOLTS_REORDER_UP_TO),
                    new CatalogSku(DRILLS, "Cordless drill", 8, 40)
                )
            ),
            new SimulatorProperties.Traffic(2.0, 12),
            new SimulatorProperties.Supplier(true, leadTime, partialShipments),
            new SimulatorProperties.Scenarios(WH_1, "DEMO-", Duration.ZERO, Duration.ofSeconds(5), 600, 40, 4),
            new SimulatorProperties.Feed(50)
        );
    }

    public static StockLevelEvent stockLevelEvent(StockKey key, int previousLevel, int newLevel,
                                                  MovementReason reason) {
        return new StockLevelEvent(UUID.randomUUID(), key.sku(), key.warehouseId(), newLevel, previousLevel, reason,
            OCCURRED_AT);
    }

    public static PurchaseOrderPlacedEvent orderPlaced(StockKey key, int quantity, Instant placedAt) {
        return new PurchaseOrderPlacedEvent(UUID.randomUUID(), key.sku(), key.warehouseId(), quantity, placedAt);
    }

    public static PurchaseOrderCompletedEvent orderCompleted(StockKey key, int quantity, Instant placedAt) {
        return new PurchaseOrderCompletedEvent(UUID.randomUUID(), key.sku(), key.warehouseId(), quantity, placedAt,
            OCCURRED_AT);
    }

    public static PurchaseOrderExpiredEvent orderExpired(StockKey key, int quantity, int received) {
        return new PurchaseOrderExpiredEvent(UUID.randomUUID(), key.sku(), key.warehouseId(), quantity, received,
            OCCURRED_AT);
    }
}
