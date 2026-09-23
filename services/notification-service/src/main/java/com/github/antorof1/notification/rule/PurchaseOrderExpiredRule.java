package com.github.antorof1.notification.rule;

import com.github.antorof1.events.purchaseorder.PurchaseOrderExpiredEvent;
import com.github.antorof1.notification.dispatch.Notification;
import com.github.antorof1.notification.dispatch.NotificationSeverity;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PurchaseOrderExpiredRule implements NotificationRule<PurchaseOrderExpiredEvent> {
    @Override
    public Class<PurchaseOrderExpiredEvent> eventType() {
        return PurchaseOrderExpiredEvent.class;
    }

    @Override
    public Optional<Notification> evaluate(PurchaseOrderExpiredEvent event) {
        return Optional.of(new Notification(
            "Purchase order expired: %s".formatted(event.sku()),
            "Only %d of %d units of %s were received at warehouse %s before the order expired.".formatted(
                event.receivedQuantity(),
                event.orderedQuantity(),
                event.sku(),
                event.warehouseId()
            ),
            NotificationSeverity.WARNING
        ));
    }
}
