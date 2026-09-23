package com.github.antorof1.notification.dispatch;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.notification.rule.BulkMovementRule;
import com.github.antorof1.notification.rule.DamagedStockRule;
import com.github.antorof1.notification.rule.NegativeStockRule;
import com.github.antorof1.notification.rule.NotificationRule;
import com.github.antorof1.notification.sender.NotificationSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.github.antorof1.notification.utils.NotificationUtils.BULK_THRESHOLD;
import static com.github.antorof1.notification.utils.NotificationUtils.SKU;
import static com.github.antorof1.notification.utils.NotificationUtils.movementEvent;
import static com.github.antorof1.notification.utils.NotificationUtils.placedEvent;
import static com.github.antorof1.notification.utils.NotificationUtils.stockLevelEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {
    @Mock
    private NotificationSender sender;

    private NotificationDispatcher dispatcher() {
        List<NotificationRule<?>> rules = List.of(
            new BulkMovementRule(BULK_THRESHOLD), new DamagedStockRule(), new NegativeStockRule()
        );

        return new NotificationDispatcher(sender, rules);
    }

    @Test
    void sendsNothingForAnEventNoRuleHandles() {
        dispatcher().process(placedEvent(SKU, 42));

        verifyNoInteractions(sender);
    }

    @Test
    void sendsNothingWhenTheMatchingRuleProducesNoNotification() {
        dispatcher().process(stockLevelEvent(SKU, 5, 3, MovementReason.PICK));

        verifyNoInteractions(sender);
    }

    @Test
    void sendsTheNotificationOfTheOneMatchingRule() {
        dispatcher().process(stockLevelEvent(SKU, 2, -1, MovementReason.PICK));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().severity()).isEqualTo(NotificationSeverity.CRITICAL);
    }

    @Test
    void sendsOneNotificationPerMatchingRule() {
        MovementEvent event = movementEvent(SKU, -BULK_THRESHOLD, MovementReason.DAMAGE);

        dispatcher().process(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(sender, times(2)).send(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(Notification::label)
            .containsExactlyInAnyOrder("Bulk movement: " + SKU, "Damaged stock: " + SKU);
    }

    @Test
    void sendsNothingWithoutRules() {
        new NotificationDispatcher(sender, List.of()).process(movementEvent(SKU, 1, MovementReason.DAMAGE));

        verifyNoInteractions(sender);
    }
}
