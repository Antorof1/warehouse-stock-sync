package com.github.antorof1.simulator.scenario;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.simulator.state.StockKey;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class NotificationRulesScenario implements Scenario {
    static final int OPENING_STOCK = 20;
    static final int DAMAGED = 3;

    @Override
    public String id() {
        return "notification-rules";
    }

    @Override
    public String name() {
        return "Notification rules";
    }

    @Override
    public String description() {
        return "Damaged stock, a bulk movement and negative stock each raise an alert in notification-service.";
    }

    @Override
    public void run(ScenarioContext context) throws InterruptedException {
        StockKey key = context.newStockKey("ALERTS");
        int bulk = context.settings().bulkQuantity();
        int level = OPENING_STOCK;

        context.info("Follow the alerts with: docker compose logs -f notification-service");

        context.step("Receive %d units of %s".formatted(OPENING_STOCK, key));
        context.record(key, OPENING_STOCK, MovementReason.RECEIPT);
        context.awaitLevel(key, level);
        context.pause();

        context.step("Write off %d damaged units".formatted(DAMAGED));
        context.record(key, -DAMAGED, MovementReason.DAMAGE);
        level -= DAMAGED;
        context.awaitLevel(key, level);
        context.info("Expect a WARNING from DamagedStockRule");
        context.pause();

        context.step("Receive a bulk delivery of %d units".formatted(bulk));
        context.record(key, bulk, MovementReason.RECEIPT);
        level += bulk;
        context.awaitLevel(key, level);
        context.info("Expect a WARNING from BulkMovementRule");
        context.pause();

        int oversold = level + OPENING_STOCK;
        context.step("Pick %d units, more than the %d in stock".formatted(oversold, level));
        context.record(key, -oversold, MovementReason.PICK);
        level -= oversold;
        context.awaitLevel(key, level);
        context.info("Expect a CRITICAL from NegativeStockRule, plus another bulk movement WARNING");
        context.pause();

        context.step("A cycle count corrects the books by %d units".formatted(-level));
        context.record(key, -level, MovementReason.ADJUSTMENT);
        context.awaitLevel(key, 0);
    }
}
