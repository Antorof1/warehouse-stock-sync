package com.github.antorof1.notification.rule;

import com.github.antorof1.events.MovementReason;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static com.github.antorof1.notification.utils.NotificationUtils.SKU;
import static com.github.antorof1.notification.utils.NotificationUtils.movementEvent;
import static org.assertj.core.api.Assertions.assertThat;

class BulkMovementRuleConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withBean(BulkMovementRule.class);

    @Test
    void readsTheThresholdFromConfiguration() {
        contextRunner
            .withPropertyValues("app.notification.rules.bulk-movement.threshold=10")
            .run(context -> {
                BulkMovementRule rule = context.getBean(BulkMovementRule.class);

                assertThat(rule.evaluate(movementEvent(SKU, 9, MovementReason.RECEIPT))).isEmpty();
                assertThat(rule.evaluate(movementEvent(SKU, 10, MovementReason.RECEIPT))).isPresent();
            });
    }

    @Test
    void failsToStartWithoutAThreshold() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }
}
