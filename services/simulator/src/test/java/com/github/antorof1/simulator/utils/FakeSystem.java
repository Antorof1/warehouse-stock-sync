package com.github.antorof1.simulator.utils;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.Topics;
import com.github.antorof1.simulator.client.Movement;
import com.github.antorof1.simulator.client.ReorderClient;
import com.github.antorof1.simulator.client.WarehouseClient;
import com.github.antorof1.simulator.config.SimulatorProperties;
import com.github.antorof1.simulator.feed.EventFeed;
import com.github.antorof1.simulator.feed.EventTypes;
import com.github.antorof1.simulator.feed.FeedEntry;
import com.github.antorof1.simulator.state.SimulationState;
import com.github.antorof1.simulator.state.StockKey;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.antorof1.simulator.utils.SimulatorUtils.stockLevelEvent;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

public class FakeSystem {
    private final SimulationState state = new SimulationState();
    private final EventFeed feed;
    private final WarehouseClient warehouseClient = mock(WarehouseClient.class);
    private final ReorderClient reorderClient = mock(ReorderClient.class);

    private final Map<StockKey, Integer> levels = new HashMap<>();
    private final Map<StockKey, int[]> rules = new HashMap<>();
    private final Map<StockKey, int[]> openOrders = new HashMap<>();
    private final List<Movement> movements = new ArrayList<>();
    private long offset;

    public FakeSystem(SimulatorProperties properties) {
        this.feed = new EventFeed(properties);

        willAnswer(invocation -> {
            apply(invocation.getArgument(0));
            return null;
        }).given(warehouseClient).record(any());

        willAnswer(invocation -> {
            synchronized (this) {
                rules.put(invocation.getArgument(0), new int[]{invocation.getArgument(1), invocation.getArgument(2)});
            }
            return null;
        }).given(reorderClient).upsertRule(any(), anyInt(), anyInt());
    }

    public SimulationState state() {
        return state;
    }

    public EventFeed feed() {
        return feed;
    }

    public WarehouseClient warehouseClient() {
        return warehouseClient;
    }

    public ReorderClient reorderClient() {
        return reorderClient;
    }

    public synchronized List<Movement> movements() {
        return List.copyOf(movements);
    }

    public synchronized int level(StockKey key) {
        return levels.getOrDefault(key, 0);
    }

    private synchronized void apply(Movement movement) {
        movements.add(movement);

        StockKey key = new StockKey(movement.warehouseId(), movement.sku());
        int previous = levels.getOrDefault(key, 0);
        int current = previous + movement.quantityDelta();
        levels.put(key, current);

        state.onStockLevel(stockLevelEvent(key, previous, current, movement.reason()));
        feed.append(new FeedEntry(Topics.STOCK_LEVEL_EVENTS + "-0-" + offset, Topics.STOCK_LEVEL_EVENTS, 0,
            offset++, key.sku(), EventTypes.STOCK_LEVEL_CHANGED, Instant.now(), null, "{}", null));

        reorder(key, current, movement);
    }

    private void reorder(StockKey key, int level, Movement movement) {
        int[] rule = rules.get(key);
        if (rule == null) return;

        int[] order = openOrders.get(key);
        if (order != null && movement.reason() == MovementReason.RECEIPT) {
            order[1] += movement.quantityDelta();
            if (order[1] >= order[0]) {
                openOrders.remove(key);
                state.onOrderCompleted(SimulatorUtils.orderCompleted(key, order[0], Instant.now()));
                order = null;
            }
        }

        if (order == null && level <= rule[0]) {
            int quantity = rule[1] - level;
            openOrders.put(key, new int[]{quantity, 0});
            state.onOrderPlaced(SimulatorUtils.orderPlaced(key, quantity, Instant.now()));
        }
    }
}
