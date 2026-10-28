package com.github.antorof1.simulator.traffic;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.simulator.catalog.Catalog;
import com.github.antorof1.simulator.catalog.Catalog.CatalogItem;
import com.github.antorof1.simulator.client.Movement;
import com.github.antorof1.simulator.client.ReorderClient;
import com.github.antorof1.simulator.client.ReorderRule;
import com.github.antorof1.simulator.client.WarehouseClient;
import com.github.antorof1.simulator.state.SimulationState;
import com.github.antorof1.simulator.state.StockKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CatalogSeeder {
    private static final Logger log = LoggerFactory.getLogger(CatalogSeeder.class);

    private final Catalog catalog;
    private final ReorderClient reorderClient;
    private final WarehouseClient warehouseClient;
    private final SimulationState state;
    private final Set<StockKey> received = ConcurrentHashMap.newKeySet();
    private volatile boolean seeded;

    public CatalogSeeder(Catalog catalog, ReorderClient reorderClient, WarehouseClient warehouseClient,
                         SimulationState state) {
        this.catalog = catalog;
        this.reorderClient = reorderClient;
        this.warehouseClient = warehouseClient;
        this.state = state;
    }

    public boolean isSeeded() {
        return seeded;
    }

    public void seed() {
        for (CatalogItem item : catalog.items()) {
            reorderClient.upsertRule(item.key(), item.reorderPoint(), item.reorderUpTo());
            state.putRule(new ReorderRule(item.key().sku(), item.key().warehouseId(), item.reorderPoint(),
                item.reorderUpTo()));

            if (state.level(item.key()).orElse(0) <= 0 && !received.contains(item.key())) {
                warehouseClient.record(Movement.of(item.key(), item.reorderUpTo(), MovementReason.RECEIPT));
                received.add(item.key());
            }
        }

        seeded = true;
        log.info("Seeded {} catalog items", catalog.items().size());
    }
}
