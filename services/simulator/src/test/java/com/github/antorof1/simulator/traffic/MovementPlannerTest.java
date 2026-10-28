package com.github.antorof1.simulator.traffic;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.simulator.catalog.Catalog;
import com.github.antorof1.simulator.client.Movement;
import com.github.antorof1.simulator.config.SimulatorProperties;
import com.github.antorof1.simulator.state.StockKey;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import static com.github.antorof1.simulator.utils.SimulatorUtils.properties;
import static org.assertj.core.api.Assertions.assertThat;

class MovementPlannerTest {
    private static final int ROUNDS = 5_000;

    private final SimulatorProperties properties = properties();
    private final Catalog catalog = new Catalog(properties);
    private final MovementPlanner planner = new MovementPlanner(catalog, properties);

    @Test
    void neverTakesStockBelowZero() {
        RandomGenerator random = new SplittableRandom(42);
        Map<StockKey, Integer> levels = new HashMap<>();
        catalog.items().forEach(item -> levels.put(item.key(), 30));

        for (int i = 0; i < ROUNDS; i++) {
            for (Movement movement : planner.next(random, levels)) {
                StockKey key = new StockKey(movement.warehouseId(), movement.sku());
                int level = levels.getOrDefault(key, 0) + movement.quantityDelta();

                assertThat(level).as("level of %s after %s", key, movement).isNotNegative();
                levels.put(key, level);
            }
        }
    }

    @Test
    void drainsStockWithoutReceipts() {
        RandomGenerator random = new SplittableRandom(7);
        Map<StockKey, Integer> levels = new HashMap<>();
        catalog.items().forEach(item -> levels.put(item.key(), item.reorderUpTo()));
        int startingTotal = total(levels);

        for (int i = 0; i < ROUNDS; i++) {
            for (Movement movement : planner.next(random, levels)) {
                assertThat(movement.reason()).isNotEqualTo(MovementReason.RECEIPT);
                levels.merge(new StockKey(movement.warehouseId(), movement.sku()), movement.quantityDelta(),
                    Integer::sum);
            }
        }

        assertThat(total(levels)).isLessThan(startingTotal / 4);
    }

    @Test
    void transfersTheSameQuantityOutAndIn() {
        RandomGenerator random = new SplittableRandom(3);
        Map<StockKey, Integer> levels = new HashMap<>();
        catalog.items().forEach(item -> levels.put(item.key(), item.key().warehouseId().equals("WH-1") ? 100 : 0));

        boolean sawTransfer = false;
        for (int i = 0; i < 500; i++) {
            List<Movement> movements = planner.next(random, levels);
            if (movements.size() == 2) {
                sawTransfer = true;
                Movement out = movements.get(0);
                Movement in = movements.get(1);

                assertThat(out.reason()).isEqualTo(MovementReason.TRANSFER_OUT);
                assertThat(in.reason()).isEqualTo(MovementReason.TRANSFER_IN);
                assertThat(out.sku()).isEqualTo(in.sku());
                assertThat(out.warehouseId()).isEqualTo("WH-1");
                assertThat(in.warehouseId()).isEqualTo("WH-2");
                assertThat(out.quantityDelta()).isNegative().isEqualTo(-in.quantityDelta());
            }
        }

        assertThat(sawTransfer).isTrue();
    }

    @Test
    void sendsNothingButAdjustmentsWhenStockIsEmpty() {
        RandomGenerator random = new SplittableRandom(11);

        for (int i = 0; i < 500; i++) {
            for (Movement movement : planner.next(random, Map.of())) {
                assertThat(movement.reason()).isEqualTo(MovementReason.ADJUSTMENT);
                assertThat(movement.quantityDelta()).isPositive();
            }
        }
    }

    @Test
    void keepsPicksWithinTheConfiguredMaximum() {
        RandomGenerator random = new SplittableRandom(5);
        Map<StockKey, Integer> levels = new HashMap<>();
        catalog.items().forEach(item -> levels.put(item.key(), 10_000));

        for (int i = 0; i < 1_000; i++) {
            planner.next(random, levels).stream()
                .filter(movement -> movement.reason() == MovementReason.PICK)
                .forEach(movement -> assertThat(-movement.quantityDelta())
                    .isBetween(1, properties.traffic().maxPick()));
        }
    }

    private static int total(Map<StockKey, Integer> levels) {
        return levels.values().stream().mapToInt(Integer::intValue).sum();
    }
}
