package com.github.antorof1.simulator.traffic;

import com.github.antorof1.simulator.client.Movement;
import com.github.antorof1.simulator.state.StockKey;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

class InFlightDecreases {
    static final Duration WINDOW = Duration.ofSeconds(3);

    private final Deque<Decrease> decreases = new ArrayDeque<>();

    synchronized void record(Movement movement, Instant at) {
        if (movement.quantityDelta() < 0) {
            decreases.addLast(new Decrease(new StockKey(movement.warehouseId(), movement.sku()),
                movement.quantityDelta(), at));
        }
    }

    synchronized Map<StockKey, Integer> adjust(Map<StockKey, Integer> levels, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!decreases.isEmpty() && decreases.peekFirst().at().isBefore(cutoff)) {
            decreases.removeFirst();
        }

        Map<StockKey, Integer> adjusted = new HashMap<>(levels);
        decreases.forEach(decrease -> adjusted.merge(decrease.key(), decrease.delta(), Integer::sum));

        return adjusted;
    }

    private record Decrease(StockKey key, int delta, Instant at) {
    }
}
