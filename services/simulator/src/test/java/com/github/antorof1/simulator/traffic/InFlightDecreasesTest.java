package com.github.antorof1.simulator.traffic;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.simulator.client.Movement;
import com.github.antorof1.simulator.state.StockKey;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.antorof1.simulator.utils.SimulatorUtils.BOLTS_WH_1;
import static com.github.antorof1.simulator.utils.SimulatorUtils.BOLTS_WH_2;
import static com.github.antorof1.simulator.utils.SimulatorUtils.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;

class InFlightDecreasesTest {
    private final InFlightDecreases inFlight = new InFlightDecreases();

    @Test
    void subtractsRecentDecreasesFromKnownLevels() {
        inFlight.record(Movement.of(BOLTS_WH_1, -5, MovementReason.PICK), OCCURRED_AT);
        inFlight.record(Movement.of(BOLTS_WH_1, -2, MovementReason.DAMAGE), OCCURRED_AT);

        Map<StockKey, Integer> adjusted = inFlight.adjust(Map.of(BOLTS_WH_1, 10, BOLTS_WH_2, 4),
            OCCURRED_AT.plusSeconds(1));

        assertThat(adjusted).containsEntry(BOLTS_WH_1, 3).containsEntry(BOLTS_WH_2, 4);
    }

    @Test
    void ignoresIncreases() {
        inFlight.record(Movement.of(BOLTS_WH_1, 5, MovementReason.TRANSFER_IN), OCCURRED_AT);

        assertThat(inFlight.adjust(Map.of(BOLTS_WH_1, 10), OCCURRED_AT)).containsEntry(BOLTS_WH_1, 10);
    }

    @Test
    void forgetsDecreasesOlderThanTheWindow() {
        inFlight.record(Movement.of(BOLTS_WH_1, -5, MovementReason.PICK), OCCURRED_AT);

        Map<StockKey, Integer> adjusted = inFlight.adjust(Map.of(BOLTS_WH_1, 10),
            OCCURRED_AT.plus(InFlightDecreases.WINDOW).plusMillis(1));

        assertThat(adjusted).containsEntry(BOLTS_WH_1, 10);
    }

    @Test
    void coversItemsWithNoKnownLevelYet() {
        inFlight.record(Movement.of(BOLTS_WH_1, -1, MovementReason.ADJUSTMENT), OCCURRED_AT);

        assertThat(inFlight.adjust(Map.of(), OCCURRED_AT)).containsEntry(BOLTS_WH_1, -1);
    }
}
