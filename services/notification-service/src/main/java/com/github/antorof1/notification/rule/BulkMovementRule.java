package com.github.antorof1.notification.rule;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.notification.dispatch.Notification;
import com.github.antorof1.notification.dispatch.NotificationSeverity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class BulkMovementRule implements NotificationRule<MovementEvent> {
    private final int threshold;

    public BulkMovementRule(@Value("${app.notification.rules.bulk-movement.threshold}") int threshold) {
        this.threshold = threshold;
    }

    @Override
    public Optional<Notification> evaluate(MovementEvent event) {
        int quantity = Math.abs(event.quantityDelta());
        if (quantity < threshold) return Optional.empty();

        return Optional.of(new Notification(
            "Bulk movement: %s".formatted(event.sku()),
            "%d units of %s were %s warehouse %s (reason: %s).".formatted(
                quantity,
                event.sku(),
                event.quantityDelta() > 0 ? "added to" : "removed from",
                event.warehouseId(),
                event.reason()
            ),
            NotificationSeverity.WARNING
        ));
    }
}
