package com.github.antorof1.notification.rule;

import com.github.antorof1.events.purchaseorder.PurchaseOrderPlacedEvent;
import com.github.antorof1.notification.dispatch.Notification;
import com.github.antorof1.notification.dispatch.NotificationSeverity;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PurchaseOrderPlacedRule implements NotificationRule<PurchaseOrderPlacedEvent> {
    @Override
    public Class<PurchaseOrderPlacedEvent> eventType() {
        return PurchaseOrderPlacedEvent.class;
    }

    @Override
    public Optional<Notification> evaluate(PurchaseOrderPlacedEvent event) {
        return Optional.of(new Notification(
            "Purchase order placed: %s".formatted(event.sku()),
            "%d units of %s were ordered for warehouse %s."
                .formatted(event.quantity(), event.sku(), event.warehouseId()),
            NotificationSeverity.INFO
        ));
    }
}
