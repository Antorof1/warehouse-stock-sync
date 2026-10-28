package com.github.antorof1.simulator.traffic;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.simulator.catalog.Catalog;
import com.github.antorof1.simulator.catalog.Catalog.CatalogItem;
import com.github.antorof1.simulator.client.Movement;
import com.github.antorof1.simulator.config.SimulatorProperties;
import com.github.antorof1.simulator.state.StockKey;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

@Component
public class MovementPlanner {
    private static final int PICK_PERCENT = 70;
    private static final int TRANSFER_PERCENT = 15;
    private static final int ADJUSTMENT_PERCENT = 12;

    private static final int MIN_TRANSFER_GAP = 10;
    private static final int MAX_TRANSFER = 10;
    private static final int MAX_ADJUSTMENT = 3;
    private static final int MAX_DAMAGE = 2;

    private final Catalog catalog;
    private final int maxPick;

    public MovementPlanner(Catalog catalog, SimulatorProperties properties) {
        this.catalog = catalog;
        this.maxPick = properties.traffic().maxPick();
    }

    public List<Movement> next(RandomGenerator random, Map<StockKey, Integer> levels) {
        List<CatalogItem> items = catalog.items();
        if (items.isEmpty()) return List.of();

        CatalogItem item = items.get(random.nextInt(items.size()));
        int level = levels.getOrDefault(item.key(), 0);
        int roll = random.nextInt(100);

        if (roll < PICK_PERCENT) {
            return pick(item.key(), level, random);
        }
        if (roll < PICK_PERCENT + TRANSFER_PERCENT) {
            return transfer(item.key(), level, levels, random);
        }
        if (roll < PICK_PERCENT + TRANSFER_PERCENT + ADJUSTMENT_PERCENT) {
            return adjustment(item.key(), level, random);
        }

        return damage(item.key(), level, random);
    }

    private List<Movement> pick(StockKey key, int level, RandomGenerator random) {
        if (level <= 0) return List.of();

        int quantity = 1 + random.nextInt(Math.clamp(level / 2, 1, maxPick));

        return List.of(Movement.of(key, -quantity, MovementReason.PICK));
    }

    private List<Movement> transfer(StockKey source, int level, Map<StockKey, Integer> levels,
                                    RandomGenerator random) {
        StockKey target = catalog.warehouses().stream()
            .filter(warehouseId -> !warehouseId.equals(source.warehouseId()))
            .map(warehouseId -> new StockKey(warehouseId, source.sku()))
            .min(Comparator.comparingInt(key -> levels.getOrDefault(key, 0)))
            .orElse(null);

        if (target == null) return List.of();

        int gap = level - levels.getOrDefault(target, 0);
        if (gap < MIN_TRANSFER_GAP) return List.of();

        int quantity = 1 + random.nextInt(Math.min(MAX_TRANSFER, gap / 2));

        return List.of(
            Movement.of(source, -quantity, MovementReason.TRANSFER_OUT),
            Movement.of(target, quantity, MovementReason.TRANSFER_IN)
        );
    }

    private List<Movement> adjustment(StockKey key, int level, RandomGenerator random) {
        int quantity = 1 + random.nextInt(MAX_ADJUSTMENT);
        boolean down = random.nextBoolean() && level > MAX_ADJUSTMENT;

        return List.of(Movement.of(key, down ? -quantity : quantity, MovementReason.ADJUSTMENT));
    }

    private List<Movement> damage(StockKey key, int level, RandomGenerator random) {
        if (level <= MAX_DAMAGE) return List.of();

        int quantity = 1 + random.nextInt(MAX_DAMAGE);

        return List.of(Movement.of(key, -quantity, MovementReason.DAMAGE));
    }
}
