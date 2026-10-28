package com.github.antorof1.simulator.traffic;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.simulator.catalog.Catalog;
import com.github.antorof1.simulator.client.Movement;
import com.github.antorof1.simulator.client.ReorderClient;
import com.github.antorof1.simulator.client.WarehouseClient;
import com.github.antorof1.simulator.config.SimulatorProperties;
import com.github.antorof1.simulator.state.SimulationState;
import org.junit.jupiter.api.Test;

import static com.github.antorof1.simulator.utils.SimulatorUtils.BOLTS_REORDER_POINT;
import static com.github.antorof1.simulator.utils.SimulatorUtils.BOLTS_REORDER_UP_TO;
import static com.github.antorof1.simulator.utils.SimulatorUtils.BOLTS_WH_1;
import static com.github.antorof1.simulator.utils.SimulatorUtils.BOLTS_WH_2;
import static com.github.antorof1.simulator.utils.SimulatorUtils.DRILLS_WH_1;
import static com.github.antorof1.simulator.utils.SimulatorUtils.properties;
import static com.github.antorof1.simulator.utils.SimulatorUtils.stockLevelEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CatalogSeederTest {
    private final SimulatorProperties properties = properties();
    private final SimulationState state = new SimulationState();
    private final ReorderClient reorderClient = mock(ReorderClient.class);
    private final WarehouseClient warehouseClient = mock(WarehouseClient.class);
    private final CatalogSeeder seeder = new CatalogSeeder(new Catalog(properties), reorderClient, warehouseClient,
        state);

    @Test
    void createsARuleAndOpeningStockForEveryItem() {
        seeder.seed();

        assertThat(seeder.isSeeded()).isTrue();
        verify(reorderClient).upsertRule(BOLTS_WH_1, BOLTS_REORDER_POINT, BOLTS_REORDER_UP_TO);
        verify(reorderClient, times(4)).upsertRule(any(), anyInt(), anyInt());
        verify(warehouseClient).record(Movement.of(BOLTS_WH_1, BOLTS_REORDER_UP_TO, MovementReason.RECEIPT));
        verify(warehouseClient, times(4)).record(any());
        assertThat(state.rules()).containsKey(BOLTS_WH_1);
    }

    @Test
    void skipsOpeningStockForItemsThatHaveStock() {
        state.onStockLevel(stockLevelEvent(BOLTS_WH_1, 0, 12, MovementReason.RECEIPT));

        seeder.seed();

        verify(warehouseClient, never()).record(Movement.of(BOLTS_WH_1, BOLTS_REORDER_UP_TO, MovementReason.RECEIPT));
        verify(warehouseClient).record(Movement.of(BOLTS_WH_2, BOLTS_REORDER_UP_TO, MovementReason.RECEIPT));
    }

    @Test
    void stocksItemsThatAreKnownButEmpty() {
        state.onStockLevel(stockLevelEvent(DRILLS_WH_1, 3, 0, MovementReason.ADJUSTMENT));

        seeder.seed();

        verify(warehouseClient).record(Movement.of(DRILLS_WH_1, 40, MovementReason.RECEIPT));
    }

    @Test
    void receivesOpeningStockOnlyOnceAcrossRetries() {
        seeder.seed();
        seeder.seed();

        verify(warehouseClient, times(4)).record(any());
    }
}
