package com.github.antorof1.simulator.supplier;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.simulator.catalog.Catalog;
import com.github.antorof1.simulator.client.Movement;
import com.github.antorof1.simulator.client.WarehouseClient;
import com.github.antorof1.simulator.config.SimulatorProperties;
import com.github.antorof1.simulator.state.PurchaseOrderView;
import com.github.antorof1.simulator.state.SimulationState;
import com.github.antorof1.simulator.state.StockKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SupplierSimulator {
    static final double PARTIAL_SHIPMENT_SHARE = 0.6;
    static final Duration RETRY_DELAY = Duration.ofSeconds(5);

    private static final Logger log = LoggerFactory.getLogger(SupplierSimulator.class);

    private final SimulationState state;
    private final Catalog catalog;
    private final WarehouseClient warehouseClient;
    private final Clock clock;
    private final Instant startedAt;
    private final boolean partialShipments;
    private final Map<StockKey, Delivery> deliveries = new ConcurrentHashMap<>();
    private final AtomicLong shipments = new AtomicLong();
    private final AtomicLong unitsDelivered = new AtomicLong();
    private volatile boolean enabled;
    private volatile Duration leadTime;

    public SupplierSimulator(SimulationState state, Catalog catalog, WarehouseClient warehouseClient, Clock clock,
                             SimulatorProperties properties) {
        this.state = state;
        this.catalog = catalog;
        this.warehouseClient = warehouseClient;
        this.clock = clock;
        this.startedAt = clock.instant();
        this.enabled = properties.supplier().enabled();
        this.leadTime = properties.supplier().leadTime();
        this.partialShipments = properties.supplier().partialShipments();
    }

    public void configure(boolean enabled, Duration leadTime) {
        this.enabled = enabled;
        this.leadTime = leadTime;
    }

    public SupplierStatus status() {
        long inTransit = deliveries.values().stream().filter(delivery -> !delivery.isComplete()).count();

        return new SupplierStatus(enabled, leadTime.toSeconds(), partialShipments, inTransit, shipments.get(),
            unitsDelivered.get());
    }

    @Scheduled(fixedDelay = 1000)
    public void deliver() {
        Map<StockKey, PurchaseOrderView> openOrders = state.openOrders();
        deliveries.keySet().retainAll(openOrders.keySet());

        if (!enabled) return;

        Instant now = clock.instant();
        openOrders.forEach((key, order) -> {
            if (catalog.contains(key) && !order.placedAt().isBefore(startedAt)) {
                deliver(key, order, now);
            }
        });
    }

    private void deliver(StockKey key, PurchaseOrderView order, Instant now) {
        Delivery delivery = deliveries.compute(key, (k, existing) ->
            existing != null && existing.placedAt().equals(order.placedAt())
                ? existing
                : new Delivery(order.placedAt(), order.quantity(), 0, order.placedAt().plus(leadTime))
        );

        if (delivery.isComplete() || now.isBefore(delivery.nextShipmentAt())) return;

        int quantity = shipmentSize(delivery);
        try {
            warehouseClient.record(Movement.of(key, quantity, MovementReason.RECEIPT));
            deliveries.put(key, delivery.shipped(quantity, now.plus(leadTime)));
            shipments.incrementAndGet();
            unitsDelivered.addAndGet(quantity);
            log.info("Supplier delivered {} of {} units for {}", delivery.shipped() + quantity,
                delivery.quantity(), key);
        } catch (RestClientException e) {
            deliveries.put(key, delivery.retryAt(now.plus(RETRY_DELAY)));
            log.warn("Supplier delivery for {} failed: {}", key, e.getMessage());
        }
    }

    private int shipmentSize(Delivery delivery) {
        int remaining = delivery.quantity() - delivery.shipped();
        if (partialShipments && delivery.shipped() == 0 && delivery.quantity() > 1) {
            return (int) Math.ceil(delivery.quantity() * PARTIAL_SHIPMENT_SHARE);
        }

        return remaining;
    }

    private record Delivery(Instant placedAt, int quantity, int shipped, Instant nextShipmentAt) {
        boolean isComplete() {
            return shipped >= quantity;
        }

        Delivery shipped(int units, Instant nextShipmentAt) {
            return new Delivery(placedAt, quantity, shipped + units, nextShipmentAt);
        }

        Delivery retryAt(Instant nextShipmentAt) {
            return new Delivery(placedAt, quantity, shipped, nextShipmentAt);
        }
    }

    public record SupplierStatus(
        boolean enabled,
        long leadTimeSeconds,
        boolean partialShipments,
        long ordersInTransit,
        long shipments,
        long unitsDelivered
    ) {
    }
}
