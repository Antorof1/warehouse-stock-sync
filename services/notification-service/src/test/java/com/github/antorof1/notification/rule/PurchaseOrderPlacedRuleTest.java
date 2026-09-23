package com.github.antorof1.notification.rule;

import com.github.antorof1.notification.dispatch.Notification;
import com.github.antorof1.notification.dispatch.NotificationSeverity;
import org.junit.jupiter.api.Test;

import static com.github.antorof1.notification.utils.NotificationUtils.SKU;
import static com.github.antorof1.notification.utils.NotificationUtils.WAREHOUSE_ID;
import static com.github.antorof1.notification.utils.NotificationUtils.placedEvent;
import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderPlacedRuleTest {
    private final PurchaseOrderPlacedRule rule = new PurchaseOrderPlacedRule();

    @Test
    void notifiesEveryPlacedOrder() {
        assertThat(rule.evaluate(placedEvent(SKU, 42))).contains(new Notification(
            "Purchase order placed: " + SKU,
            "42 units of %s were ordered for warehouse %s.".formatted(SKU, WAREHOUSE_ID),
            NotificationSeverity.INFO
        ));
    }
}
