package com.github.antorof1.notification.rule;

import com.github.antorof1.notification.dispatch.Notification;
import com.github.antorof1.notification.dispatch.NotificationSeverity;
import org.junit.jupiter.api.Test;

import static com.github.antorof1.notification.utils.NotificationUtils.SKU;
import static com.github.antorof1.notification.utils.NotificationUtils.WAREHOUSE_ID;
import static com.github.antorof1.notification.utils.NotificationUtils.expiredEvent;
import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderExpiredRuleTest {
    private final PurchaseOrderExpiredRule rule = new PurchaseOrderExpiredRule();

    @Test
    void notifiesEveryExpiredOrder() {
        assertThat(rule.evaluate(expiredEvent(SKU, 42, 30))).contains(new Notification(
            "Purchase order expired: " + SKU,
            "Only 30 of 42 units of %s were received at warehouse %s before the order expired."
                .formatted(SKU, WAREHOUSE_ID),
            NotificationSeverity.WARNING
        ));
    }
}
