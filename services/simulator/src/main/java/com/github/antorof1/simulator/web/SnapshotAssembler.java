package com.github.antorof1.simulator.web;

import com.github.antorof1.simulator.catalog.Catalog;
import com.github.antorof1.simulator.catalog.Catalog.CatalogItem;
import com.github.antorof1.simulator.client.ReorderRule;
import com.github.antorof1.simulator.config.SimulatorProperties;
import com.github.antorof1.simulator.feed.EventFeed;
import com.github.antorof1.simulator.health.ServiceHealthMonitor;
import com.github.antorof1.simulator.scenario.ScenarioRunner;
import com.github.antorof1.simulator.state.PurchaseOrderView;
import com.github.antorof1.simulator.state.SimulationState;
import com.github.antorof1.simulator.state.SimulationState.StockLevel;
import com.github.antorof1.simulator.state.StockKey;
import com.github.antorof1.simulator.supplier.SupplierSimulator;
import com.github.antorof1.simulator.traffic.TrafficGenerator;
import com.github.antorof1.simulator.web.Snapshot.StockRow;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class SnapshotAssembler {
    private final SimulationState state;
    private final Catalog catalog;
    private final EventFeed feed;
    private final TrafficGenerator traffic;
    private final SupplierSimulator supplier;
    private final ScenarioRunner scenarios;
    private final ServiceHealthMonitor health;
    private final Clock clock;
    private final String scenarioSkuPrefix;

    public SnapshotAssembler(SimulationState state, Catalog catalog, EventFeed feed, TrafficGenerator traffic,
                             SupplierSimulator supplier, ScenarioRunner scenarios, ServiceHealthMonitor health,
                             Clock clock, SimulatorProperties properties) {
        this.state = state;
        this.catalog = catalog;
        this.feed = feed;
        this.traffic = traffic;
        this.supplier = supplier;
        this.scenarios = scenarios;
        this.health = health;
        this.clock = clock;
        this.scenarioSkuPrefix = properties.scenarios().skuPrefix();
    }

    public Snapshot snapshot() {
        return new Snapshot(
            clock.instant(),
            health.statuses(),
            traffic.status(),
            supplier.status(),
            scenarios.status().orElse(null),
            stockRows(),
            feed.topicCounts()
        );
    }

    private List<StockRow> stockRows() {
        Map<StockKey, StockLevel> stock = state.stock();
        Map<StockKey, ReorderRule> rules = state.rules();
        Map<StockKey, PurchaseOrderView> orders = state.orders();

        Set<StockKey> keys = new LinkedHashSet<>();
        catalog.items().forEach(item -> keys.add(item.key()));
        stock.keySet().stream()
            .filter(key -> !catalog.contains(key))
            .sorted(Comparator.comparing(StockKey::warehouseId).thenComparing(StockKey::sku))
            .forEach(keys::add);
        rules.keySet().stream()
            .filter(key -> !keys.contains(key))
            .sorted(Comparator.comparing(StockKey::warehouseId).thenComparing(StockKey::sku))
            .forEach(keys::add);

        return keys.stream()
            .map(key -> row(key, stock.get(key), rules.get(key), orders.get(key)))
            .toList();
    }

    private StockRow row(StockKey key, StockLevel level, ReorderRule rule, PurchaseOrderView order) {
        return new StockRow(
            key.warehouseId(),
            key.sku(),
            catalog.find(key).map(CatalogItem::name).orElse(null),
            key.sku().startsWith(scenarioSkuPrefix),
            level == null ? null : level.level(),
            level == null ? null : level.updatedAt(),
            Optional.ofNullable(rule).map(ReorderRule::reorderPoint).orElse(null),
            Optional.ofNullable(rule).map(ReorderRule::reorderUpTo).orElse(null),
            order
        );
    }
}
