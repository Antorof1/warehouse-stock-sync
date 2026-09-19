package com.github.antorof1.notification.rule;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.notification.dispatch.Notification;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DamagedStockRule implements NotificationRule<MovementEvent> {
    @Override
    public Optional<Notification> evaluate(MovementEvent event) {
        if (event.reason() != MovementReason.DAMAGE) return Optional.empty();

        return Optional.of(new Notification(
            "Damaged stock: %s".formatted(event.sku()),
            "%d units of %s were damaged at warehouse %s."
                .formatted(Math.abs(event.quantityDelta()), event.sku(), event.warehouseId())
        ));
    }
}
