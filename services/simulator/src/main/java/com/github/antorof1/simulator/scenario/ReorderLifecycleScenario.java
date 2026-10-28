package com.github.antorof1.simulator.scenario;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.simulator.client.ReorderClient;
import com.github.antorof1.simulator.client.ReorderRule;
import com.github.antorof1.simulator.state.PurchaseOrderView;
import com.github.antorof1.simulator.state.StockKey;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class ReorderLifecycleScenario implements Scenario {
    static final int REORDER_POINT = 10;
    static final int REORDER_UP_TO = 100;
    static final int OPENING_STOCK = 50;
    static final int PICK = 45;
    static final int PARTIAL_DELIVERY = 40;

    private final ReorderClient reorderClient;

    public ReorderLifecycleScenario(ReorderClient reorderClient) {
        this.reorderClient = reorderClient;
    }

    @Override
    public String id() {
        return "reorder-lifecycle";
    }

    @Override
    public String name() {
        return "Reorder lifecycle";
    }

    @Override
    public String description() {
        return "Stock falls below the reorder point, reorder-service places a purchase order, "
            + "and two supplier deliveries complete it.";
    }

    @Override
    public void run(ScenarioContext context) throws InterruptedException {
        StockKey key = context.newStockKey("REORDER");
        int afterPick = OPENING_STOCK - PICK;
        int ordered = REORDER_UP_TO - afterPick;

        context.step("Create a reorder rule for %s: reorder at %d, order up to %d"
            .formatted(key, REORDER_POINT, REORDER_UP_TO));
        reorderClient.upsertRule(key, REORDER_POINT, REORDER_UP_TO);
        context.state().putRule(new ReorderRule(key.sku(), key.warehouseId(), REORDER_POINT, REORDER_UP_TO));
        context.pause();

        context.step("Receive %d units".formatted(OPENING_STOCK));
        context.record(key, OPENING_STOCK, MovementReason.RECEIPT);
        context.awaitLevel(key, OPENING_STOCK);
        context.pause();

        context.step("Pick %d units, taking stock to %d, below the reorder point".formatted(PICK, afterPick));
        context.record(key, -PICK, MovementReason.PICK);
        context.awaitLevel(key, afterPick);
        context.await("reorder-service to place a purchase order", () -> context.state().openOrder(key).isPresent());
        context.info("Purchase order placed for %d units (%d - %d). It's on reorder.purchase-order-events, "
            .formatted(ordered, REORDER_UP_TO, afterPick) + "and notification-service logs it.");
        context.pause();

        context.step("The supplier delivers a partial shipment of %d units".formatted(PARTIAL_DELIVERY));
        context.record(key, PARTIAL_DELIVERY, MovementReason.RECEIPT);
        context.awaitLevel(key, afterPick + PARTIAL_DELIVERY);
        context.info("The order stays open with %d of %d units received".formatted(PARTIAL_DELIVERY, ordered));
        context.pause();

        int remaining = ordered - PARTIAL_DELIVERY;
        context.step("The supplier delivers the remaining %d units".formatted(remaining));
        context.record(key, remaining, MovementReason.RECEIPT);
        context.await("reorder-service to complete the purchase order", () -> context.state().order(key)
            .filter(order -> order.status() == PurchaseOrderView.Status.COMPLETED)
            .isPresent());
        context.awaitLevel(key, REORDER_UP_TO);
        context.info("Purchase order completed and stock is back at the target level of %d".formatted(REORDER_UP_TO));
    }
}
