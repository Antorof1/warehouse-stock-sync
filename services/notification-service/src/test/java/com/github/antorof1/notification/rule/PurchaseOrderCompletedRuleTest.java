package com.github.antorof1.notification.rule;

import com.github.antorof1.notification.dispatch.Notification;
import com.github.antorof1.notification.dispatch.NotificationSeverity;
import org.junit.jupiter.api.Test;

import static com.github.antorof1.notification.utils.NotificationUtils.SKU;
import static com.github.antorof1.notification.utils.NotificationUtils.WAREHOUSE_ID;
import static com.github.antorof1.notification.utils.NotificationUtils.completedEvent;
import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderCompletedRuleTest {
    private final PurchaseOrderCompletedRule rule = new PurchaseOrderCompletedRule();

    @Test
    void notifiesEveryCompletedOrder() {
        assertThat(rule.evaluate(completedEvent(SKU, 42))).contains(new Notification(
            "Purchase order completed: " + SKU,
            "All 42 units of %s were received at warehouse %s.".formatted(SKU, WAREHOUSE_ID),
            NotificationSeverity.INFO
        ));
    }
}
