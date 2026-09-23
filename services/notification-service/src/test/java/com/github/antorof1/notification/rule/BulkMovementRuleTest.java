package com.github.antorof1.notification.rule;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.notification.dispatch.Notification;
import com.github.antorof1.notification.dispatch.NotificationSeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.github.antorof1.notification.utils.NotificationUtils.BULK_THRESHOLD;
import static com.github.antorof1.notification.utils.NotificationUtils.SKU;
import static com.github.antorof1.notification.utils.NotificationUtils.WAREHOUSE_ID;
import static com.github.antorof1.notification.utils.NotificationUtils.movementEvent;
import static org.assertj.core.api.Assertions.assertThat;

class BulkMovementRuleTest {
    private final BulkMovementRule rule = new BulkMovementRule(BULK_THRESHOLD);

    @ParameterizedTest
    @ValueSource(ints = {0, 1, BULK_THRESHOLD - 1, -(BULK_THRESHOLD - 1)})
    void ignoresAMovementBelowTheThreshold(int quantityDelta) {
        assertThat(rule.evaluate(movementEvent(SKU, quantityDelta, MovementReason.RECEIPT))).isEmpty();
    }

    @Test
    void notifiesAMovementExactlyAtTheThreshold() {
        assertThat(rule.evaluate(movementEvent(SKU, BULK_THRESHOLD, MovementReason.RECEIPT))).isPresent();
    }

    @Test
    void describesAnInboundMovement() {
        Notification notification = rule.evaluate(movementEvent(SKU, 750, MovementReason.RECEIPT)).orElseThrow();

        assertThat(notification).isEqualTo(new Notification(
            "Bulk movement: " + SKU,
            "750 units of %s were added to warehouse %s (reason: RECEIPT).".formatted(SKU, WAREHOUSE_ID),
            NotificationSeverity.WARNING
        ));
    }

    @Test
    void describesAnOutboundMovementWithItsAbsoluteQuantity() {
        Notification notification = rule.evaluate(movementEvent(SKU, -750, MovementReason.TRANSFER_OUT)).orElseThrow();

        assertThat(notification.body()).isEqualTo(
            "750 units of %s were removed from warehouse %s (reason: TRANSFER_OUT).".formatted(SKU, WAREHOUSE_ID)
        );
    }

    @Test
    void notifiesAnOutboundMovementExactlyAtTheThreshold() {
        assertThat(rule.evaluate(movementEvent(SKU, -BULK_THRESHOLD, MovementReason.PICK))).isPresent();
    }
}
