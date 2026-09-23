package com.github.antorof1.notification.rule;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.notification.dispatch.Notification;
import com.github.antorof1.notification.dispatch.NotificationSeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.github.antorof1.notification.utils.NotificationUtils.SKU;
import static com.github.antorof1.notification.utils.NotificationUtils.WAREHOUSE_ID;
import static com.github.antorof1.notification.utils.NotificationUtils.movementEvent;
import static org.assertj.core.api.Assertions.assertThat;

class DamagedStockRuleTest {
    private final DamagedStockRule rule = new DamagedStockRule();

    @Test
    void notifiesDamagedStockWithItsAbsoluteQuantity() {
        Notification notification = rule.evaluate(movementEvent(SKU, -3, MovementReason.DAMAGE)).orElseThrow();

        assertThat(notification).isEqualTo(new Notification(
            "Damaged stock: " + SKU,
            "3 units of %s were damaged at warehouse %s.".formatted(SKU, WAREHOUSE_ID),
            NotificationSeverity.WARNING
        ));
    }

    @ParameterizedTest
    @EnumSource(value = MovementReason.class, names = "DAMAGE", mode = EnumSource.Mode.EXCLUDE)
    void ignoresEveryOtherReason(MovementReason reason) {
        assertThat(rule.evaluate(movementEvent(SKU, -3, reason))).isEmpty();
    }
}
