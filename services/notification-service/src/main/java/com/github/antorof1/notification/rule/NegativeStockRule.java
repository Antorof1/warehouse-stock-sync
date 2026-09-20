package com.github.antorof1.notification.rule;

import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.notification.dispatch.Notification;
import com.github.antorof1.notification.dispatch.NotificationSeverity;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class NegativeStockRule implements NotificationRule<StockLevelEvent> {
    @Override
    public Optional<Notification> evaluate(StockLevelEvent event) {
        if (event.newLevel() >= 0) return Optional.empty();

        return Optional.of(new Notification(
            "Negative stock: %s".formatted(event.sku()),
            "Stock for %s at warehouse %s fell to %d (from %d) after %s."
                .formatted(event.sku(), event.warehouseId(), event.newLevel(), event.previousLevel(), event.reason()),
            NotificationSeverity.CRITICAL
        ));
    }
}
