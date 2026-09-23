package com.github.antorof1.notification.rule;

import com.github.antorof1.events.purchaseorder.PurchaseOrderCompletedEvent;
import com.github.antorof1.notification.dispatch.Notification;
import com.github.antorof1.notification.dispatch.NotificationSeverity;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PurchaseOrderCompletedRule implements NotificationRule<PurchaseOrderCompletedEvent> {
    @Override
    public Class<PurchaseOrderCompletedEvent> eventType() {
        return PurchaseOrderCompletedEvent.class;
    }

    @Override
    public Optional<Notification> evaluate(PurchaseOrderCompletedEvent event) {
        return Optional.of(new Notification(
            "Purchase order completed: %s".formatted(event.sku()),
            "All %d units of %s were received at warehouse %s.".formatted(
                event.orderedQuantity(),
                event.sku(),
                event.warehouseId()
            ),
            NotificationSeverity.INFO
        ));
    }
}
