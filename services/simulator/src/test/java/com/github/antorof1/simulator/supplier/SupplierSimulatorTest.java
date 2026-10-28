package com.github.antorof1.simulator.supplier;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.simulator.catalog.Catalog;
import com.github.antorof1.simulator.client.Movement;
import com.github.antorof1.simulator.client.WarehouseClient;
import com.github.antorof1.simulator.config.SimulatorProperties;
import com.github.antorof1.simulator.state.SimulationState;
import com.github.antorof1.simulator.state.StockKey;
import com.github.antorof1.simulator.utils.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.time.Instant;

import static com.github.antorof1.simulator.utils.SimulatorUtils.BOLTS_WH_1;
import static com.github.antorof1.simulator.utils.SimulatorUtils.OCCURRED_AT;
import static com.github.antorof1.simulator.utils.SimulatorUtils.orderCompleted;
import static com.github.antorof1.simulator.utils.SimulatorUtils.orderPlaced;
import static com.github.antorof1.simulator.utils.SimulatorUtils.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SupplierSimulatorTest {
    private static final Duration LEAD_TIME = Duration.ofSeconds(8);

    private final MutableClock clock = new MutableClock(OCCURRED_AT);
    private final SimulationState state = new SimulationState();
    private final WarehouseClient warehouseClient = mock(WarehouseClient.class);

    @Test
    void shipsPartOfTheOrderAfterTheLeadTimeAndTheRestOneLeadTimeLater() {
        SupplierSimulator supplier = supplier(properties(LEAD_TIME, true));
        state.onOrderPlaced(orderPlaced(BOLTS_WH_1, 95, clock.instant()));

        supplier.deliver();
        verifyNoInteractions(warehouseClient);

        clock.advance(LEAD_TIME);
        supplier.deliver();
        verify(warehouseClient).record(receipt(BOLTS_WH_1, 57));

        supplier.deliver();
        clock.advance(LEAD_TIME);
        supplier.deliver();
        verify(warehouseClient).record(receipt(BOLTS_WH_1, 38));

        assertThat(supplier.status().shipments()).isEqualTo(2);
        assertThat(supplier.status().unitsDelivered()).isEqualTo(95);
    }

    @Test
    void shipsTheWholeOrderAtOnceWithoutPartialShipments() {
        SupplierSimulator supplier = supplier(properties(LEAD_TIME, false));
        state.onOrderPlaced(orderPlaced(BOLTS_WH_1, 95, clock.instant()));

        clock.advance(LEAD_TIME);
        supplier.deliver();

        verify(warehouseClient).record(receipt(BOLTS_WH_1, 95));
    }

    @Test
    void stopsShippingOnceTheOrderIsClosed() {
        SupplierSimulator supplier = supplier(properties(LEAD_TIME, true));
        Instant placedAt = clock.instant();
        state.onOrderPlaced(orderPlaced(BOLTS_WH_1, 95, placedAt));

        clock.advance(LEAD_TIME);
        supplier.deliver();
        state.onOrderCompleted(orderCompleted(BOLTS_WH_1, 95, placedAt));
        reset(warehouseClient);

        clock.advance(LEAD_TIME);
        supplier.deliver();

        verifyNoInteractions(warehouseClient);
        assertThat(supplier.status().ordersInTransit()).isZero();
    }

    @Test
    void ignoresOrdersPlacedBeforeTheSimulatorStarted() {
        SupplierSimulator supplier = supplier(properties(LEAD_TIME, true));
        state.onOrderPlaced(orderPlaced(BOLTS_WH_1, 95, clock.instant().minusSeconds(60)));

        clock.advance(LEAD_TIME);
        supplier.deliver();

        verifyNoInteractions(warehouseClient);
    }

    @Test
    void leavesScenarioSkusAlone() {
        SupplierSimulator supplier = supplier(properties(LEAD_TIME, true));
        state.onOrderPlaced(orderPlaced(new StockKey("WH-1", "DEMO-REORDER-X"), 95, clock.instant()));

        clock.advance(LEAD_TIME);
        supplier.deliver();

        verifyNoInteractions(warehouseClient);
    }

    @Test
    void shipsNothingWhileDisabled() {
        SupplierSimulator supplier = supplier(properties(LEAD_TIME, true));
        supplier.configure(false, LEAD_TIME);
        state.onOrderPlaced(orderPlaced(BOLTS_WH_1, 95, clock.instant()));

        clock.advance(LEAD_TIME);
        supplier.deliver();

        verifyNoInteractions(warehouseClient);
    }

    @Test
    void retriesAFailedShipment() {
        SupplierSimulator supplier = supplier(properties(LEAD_TIME, false));
        state.onOrderPlaced(orderPlaced(BOLTS_WH_1, 95, clock.instant()));
        willThrow(new ResourceAccessException("Connection refused")).given(warehouseClient).record(any());

        clock.advance(LEAD_TIME);
        supplier.deliver();
        reset(warehouseClient);

        clock.advance(SupplierSimulator.RETRY_DELAY);
        supplier.deliver();

        verify(warehouseClient).record(receipt(BOLTS_WH_1, 95));
    }

    private SupplierSimulator supplier(SimulatorProperties properties) {
        return new SupplierSimulator(state, new Catalog(properties), warehouseClient, clock, properties);
    }

    private static Movement receipt(StockKey key, int quantity) {
        return Movement.of(key, quantity, MovementReason.RECEIPT);
    }
}
