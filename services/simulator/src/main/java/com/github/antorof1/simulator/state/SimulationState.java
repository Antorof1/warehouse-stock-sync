package com.github.antorof1.simulator.state;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderCompletedEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderExpiredEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderPlacedEvent;
import com.github.antorof1.simulator.client.ReorderRule;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimulationState {
    private final Map<StockKey, StockLevel> stock = new ConcurrentHashMap<>();
    private final Map<StockKey, PurchaseOrderView> orders = new ConcurrentHashMap<>();
    private final Map<StockKey, ReorderRule> rules = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> deadLetterTopicsByKey = new ConcurrentHashMap<>();

    public void onStockLevel(StockLevelEvent event) {
        StockKey key = new StockKey(event.warehouseId(), event.sku());
        stock.put(key, new StockLevel(event.newLevel(), event.occurredAt()));

        int delta = event.newLevel() - event.previousLevel();
        if (event.reason() == MovementReason.RECEIPT && delta > 0) {
            orders.computeIfPresent(key, (k, order) -> order.isOpen() ? order.receive(delta) : order);
        }
    }

    public void onOrderPlaced(PurchaseOrderPlacedEvent event) {
        orders.put(new StockKey(event.warehouseId(), event.sku()),
            PurchaseOrderView.placed(event.quantity(), event.occurredAt()));
    }

    public void onOrderCompleted(PurchaseOrderCompletedEvent event) {
        StockKey key = new StockKey(event.warehouseId(), event.sku());
        PurchaseOrderView order = orders.getOrDefault(key,
            PurchaseOrderView.placed(event.orderedQuantity(), event.placedAt()));

        orders.put(key, order.close(PurchaseOrderView.Status.COMPLETED, event.orderedQuantity(), event.occurredAt()));
    }

    public void onOrderExpired(PurchaseOrderExpiredEvent event) {
        StockKey key = new StockKey(event.warehouseId(), event.sku());
        PurchaseOrderView order = orders.getOrDefault(key,
            PurchaseOrderView.placed(event.orderedQuantity(), event.occurredAt()));

        orders.put(key, order.close(PurchaseOrderView.Status.EXPIRED, event.receivedQuantity(), event.occurredAt()));
    }

    public void onDeadLetter(String topic, String key) {
        if (key == null) return;

        deadLetterTopicsByKey.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(topic);
    }

    public void replaceRules(Collection<ReorderRule> latest) {
        Map<StockKey, ReorderRule> byKey = new HashMap<>();
        latest.forEach(rule -> byKey.put(rule.key(), rule));

        rules.keySet().retainAll(byKey.keySet());
        rules.putAll(byKey);
    }

    public void putRule(ReorderRule rule) {
        rules.put(rule.key(), rule);
    }

    public Optional<Integer> level(StockKey key) {
        return Optional.ofNullable(stock.get(key)).map(StockLevel::level);
    }

    public Map<StockKey, Integer> levels() {
        Map<StockKey, Integer> levels = new HashMap<>();
        stock.forEach((key, level) -> levels.put(key, level.level()));

        return levels;
    }

    public Map<StockKey, StockLevel> stock() {
        return Map.copyOf(stock);
    }

    public Optional<PurchaseOrderView> order(StockKey key) {
        return Optional.ofNullable(orders.get(key));
    }

    public Optional<PurchaseOrderView> openOrder(StockKey key) {
        return order(key).filter(PurchaseOrderView::isOpen);
    }

    public Map<StockKey, PurchaseOrderView> orders() {
        return Map.copyOf(orders);
    }

    public Map<StockKey, PurchaseOrderView> openOrders() {
        Map<StockKey, PurchaseOrderView> open = new HashMap<>();
        orders.forEach((key, order) -> {
            if (order.isOpen()) open.put(key, order);
        });

        return open;
    }

    public Map<StockKey, ReorderRule> rules() {
        return Map.copyOf(rules);
    }

    public Set<String> deadLetterTopicsFor(String key) {
        return Set.copyOf(deadLetterTopicsByKey.getOrDefault(key, new HashSet<>()));
    }

    public record StockLevel(int level, Instant updatedAt) {
    }
}
