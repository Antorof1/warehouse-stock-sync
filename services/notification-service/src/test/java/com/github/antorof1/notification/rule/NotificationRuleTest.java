package com.github.antorof1.notification.rule;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.notification.dispatch.Notification;
import com.github.antorof1.notification.dispatch.NotificationSeverity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.github.antorof1.notification.utils.NotificationUtils.SKU;
import static com.github.antorof1.notification.utils.NotificationUtils.movementEvent;
import static com.github.antorof1.notification.utils.NotificationUtils.stockLevelEvent;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationRuleTest {
    private static final Notification NOTIFICATION = new Notification("label", "body", NotificationSeverity.INFO);

    private final RecordingRule rule = new RecordingRule();

    @Test
    void evaluatesAnEventOfTheRuleType() {
        MovementEvent event = movementEvent(SKU, 5, MovementReason.PICK);

        assertThat(rule.evaluateIfMatches(event)).contains(NOTIFICATION);
        assertThat(rule.evaluated).containsExactly(event);
    }

    @Test
    void skipsAnEventOfAnotherType() {
        assertThat(rule.evaluateIfMatches(stockLevelEvent(SKU, 5, 3, MovementReason.PICK))).isEmpty();
        assertThat(rule.evaluated).isEmpty();
    }

    private static final class RecordingRule implements NotificationRule<MovementEvent> {
        private final List<MovementEvent> evaluated = new ArrayList<>();

        @Override
        public Class<MovementEvent> eventType() {
            return MovementEvent.class;
        }

        @Override
        public Optional<Notification> evaluate(MovementEvent event) {
            evaluated.add(event);
            return Optional.of(NOTIFICATION);
        }
    }
}
