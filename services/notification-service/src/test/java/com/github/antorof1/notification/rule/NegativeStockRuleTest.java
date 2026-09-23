package com.github.antorof1.notification.rule;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.notification.dispatch.Notification;
import com.github.antorof1.notification.dispatch.NotificationSeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.github.antorof1.notification.utils.NotificationUtils.SKU;
import static com.github.antorof1.notification.utils.NotificationUtils.WAREHOUSE_ID;
import static com.github.antorof1.notification.utils.NotificationUtils.stockLevelEvent;
import static org.assertj.core.api.Assertions.assertThat;

class NegativeStockRuleTest {
    private final NegativeStockRule rule = new NegativeStockRule();

    @Test
    void notifiesALevelBelowZero() {
        Notification notification = rule.evaluate(stockLevelEvent(SKU, 2, -1, MovementReason.PICK)).orElseThrow();

        assertThat(notification).isEqualTo(new Notification(
            "Negative stock: " + SKU,
            "Stock for %s at warehouse %s fell to -1 (from 2) after PICK.".formatted(SKU, WAREHOUSE_ID),
            NotificationSeverity.CRITICAL
        ));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 100})
    void ignoresALevelOfZeroOrMore(int newLevel) {
        assertThat(rule.evaluate(stockLevelEvent(SKU, 5, newLevel, MovementReason.PICK))).isEmpty();
    }
}
