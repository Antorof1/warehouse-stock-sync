package com.github.antorof1.simulator.state;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.simulator.client.ReorderRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.antorof1.simulator.utils.SimulatorUtils.BOLTS;
import static com.github.antorof1.simulator.utils.SimulatorUtils.BOLTS_WH_1;
import static com.github.antorof1.simulator.utils.SimulatorUtils.BOLTS_WH_2;
import static com.github.antorof1.simulator.utils.SimulatorUtils.DRILLS;
import static com.github.antorof1.simulator.utils.SimulatorUtils.OCCURRED_AT;
import static com.github.antorof1.simulator.utils.SimulatorUtils.WH_1;
import static com.github.antorof1.simulator.utils.SimulatorUtils.WH_2;
import static com.github.antorof1.simulator.utils.SimulatorUtils.orderCompleted;
import static com.github.antorof1.simulator.utils.SimulatorUtils.orderExpired;
import static com.github.antorof1.simulator.utils.SimulatorUtils.orderPlaced;
import static com.github.antorof1.simulator.utils.SimulatorUtils.stockLevelEvent;
import static org.assertj.core.api.Assertions.assertThat;

class SimulationStateTest {
    private final SimulationState state = new SimulationState();

    @Test
    void keepsTheLatestLevelPerSkuAndWarehouse() {
        state.onStockLevel(stockLevelEvent(BOLTS_WH_1, 0, 50, MovementReason.RECEIPT));
        state.onStockLevel(stockLevelEvent(BOLTS_WH_1, 50, 38, MovementReason.PICK));
        state.onStockLevel(stockLevelEvent(BOLTS_WH_2, 0, 7, MovementReason.RECEIPT));

        assertThat(state.level(BOLTS_WH_1)).contains(38);
        assertThat(state.level(BOLTS_WH_2)).contains(7);
        assertThat(state.levels()).containsOnlyKeys(BOLTS_WH_1, BOLTS_WH_2);
    }

    @Test
    void reportsNoLevelForStockNeverSeen() {
        assertThat(state.level(BOLTS_WH_1)).isEmpty();
    }

    @Test
    void tracksAnOrderFromPlacementThroughReceiptsToCompletion() {
        state.onOrderPlaced(orderPlaced(BOLTS_WH_1, 95, OCCURRED_AT));
        state.onStockLevel(stockLevelEvent(BOLTS_WH_1, 5, 45, MovementReason.RECEIPT));

        assertThat(state.openOrder(BOLTS_WH_1)).hasValueSatisfying(order -> {
            assertThat(order.quantity()).isEqualTo(95);
            assertThat(order.received()).isEqualTo(40);
        });

        state.onOrderCompleted(orderCompleted(BOLTS_WH_1, 95, OCCURRED_AT));

        assertThat(state.openOrder(BOLTS_WH_1)).isEmpty();
        assertThat(state.order(BOLTS_WH_1)).hasValueSatisfying(order -> {
            assertThat(order.status()).isEqualTo(PurchaseOrderView.Status.COMPLETED);
            assertThat(order.received()).isEqualTo(95);
            assertThat(order.closedAt()).isEqualTo(OCCURRED_AT);
        });
    }

    @Test
    void countsOnlyReceiptsTowardAnOpenOrder() {
        state.onOrderPlaced(orderPlaced(BOLTS_WH_1, 95, OCCURRED_AT));

        state.onStockLevel(stockLevelEvent(BOLTS_WH_1, 5, 15, MovementReason.TRANSFER_IN));
        state.onStockLevel(stockLevelEvent(BOLTS_WH_1, 15, 20, MovementReason.ADJUSTMENT));

        assertThat(state.openOrder(BOLTS_WH_1)).hasValueSatisfying(order -> assertThat(order.received()).isZero());
    }

    @Test
    void recordsAnExpiredOrderWithWhatArrived() {
        state.onOrderPlaced(orderPlaced(BOLTS_WH_1, 95, OCCURRED_AT));
        state.onOrderExpired(orderExpired(BOLTS_WH_1, 95, 30));

        assertThat(state.openOrders()).isEmpty();
        assertThat(state.order(BOLTS_WH_1)).hasValueSatisfying(order -> {
            assertThat(order.status()).isEqualTo(PurchaseOrderView.Status.EXPIRED);
            assertThat(order.received()).isEqualTo(30);
        });
    }

    @Test
    void recordsACompletedOrderPlacedBeforeTheSimulatorWasWatching() {
        state.onOrderCompleted(orderCompleted(BOLTS_WH_1, 95, OCCURRED_AT));

        assertThat(state.order(BOLTS_WH_1)).hasValueSatisfying(order -> {
            assertThat(order.status()).isEqualTo(PurchaseOrderView.Status.COMPLETED);
            assertThat(order.placedAt()).isEqualTo(OCCURRED_AT);
        });
    }

    @Test
    void replacesRulesWithTheLatestList() {
        state.replaceRules(List.of(new ReorderRule(BOLTS, WH_1, 10, 50), new ReorderRule(BOLTS, WH_2, 10, 50)));
        state.replaceRules(List.of(new ReorderRule(BOLTS, WH_1, 20, 80), new ReorderRule(DRILLS, WH_1, 2, 9)));

        assertThat(state.rules()).containsOnlyKeys(BOLTS_WH_1, new StockKey(WH_1, DRILLS));
        assertThat(state.rules().get(BOLTS_WH_1).reorderPoint()).isEqualTo(20);
    }

    @Test
    void remembersWhichDeadLetterTopicsHoldAKey() {
        state.onDeadLetter("warehouse.movement-events.inventory-service.dlt", "DEMO-POISON-1");
        state.onDeadLetter("warehouse.movement-events.notification-service.dlt", "DEMO-POISON-1");
        state.onDeadLetter("warehouse.movement-events.inventory-service.dlt", null);

        assertThat(state.deadLetterTopicsFor("DEMO-POISON-1")).containsExactlyInAnyOrder(
            "warehouse.movement-events.inventory-service.dlt",
            "warehouse.movement-events.notification-service.dlt"
        );
        assertThat(state.deadLetterTopicsFor("SKU-OTHER")).isEmpty();
    }
}
