package com.github.antorof1.notification.rule;

import com.github.antorof1.events.PurchaseOrderEvent;
import com.github.antorof1.notification.dispatch.Notification;
import com.github.antorof1.notification.dispatch.NotificationSeverity;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class OrderPlacedRule implements NotificationRule<PurchaseOrderEvent> {
    @Override
    public Optional<Notification> evaluate(PurchaseOrderEvent event) {
        return Optional.of(new Notification(
            "Purchase order placed: %s".formatted(event.sku()),
            "%d units of %s were ordered for warehouse %s."
                .formatted(event.quantity(), event.sku(), event.warehouseId()),
            NotificationSeverity.INFO
        ));
    }
}
