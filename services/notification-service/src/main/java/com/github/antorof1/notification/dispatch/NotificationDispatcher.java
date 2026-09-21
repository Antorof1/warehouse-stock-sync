package com.github.antorof1.notification.dispatch;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.order.PurchaseOrderPlacedEvent;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.notification.rule.NotificationRule;
import com.github.antorof1.notification.sender.NotificationSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationDispatcher {
    private final NotificationSender sender;

    private final List<NotificationRule<MovementEvent>> movementRules;
    private final List<NotificationRule<StockLevelEvent>> levelRules;
    private final List<NotificationRule<PurchaseOrderPlacedEvent>> orderRules;

    public NotificationDispatcher(NotificationSender sender, List<NotificationRule<MovementEvent>> movementRules,
                                  List<NotificationRule<StockLevelEvent>> levelRules,
                                  List<NotificationRule<PurchaseOrderPlacedEvent>> orderRules) {
        this.sender = sender;
        this.movementRules = movementRules;
        this.levelRules = levelRules;
        this.orderRules = orderRules;
    }

    public void process(MovementEvent event) {
        movementRules.stream()
            .map(rule -> rule.evaluate(event))
            .forEach(notification -> notification.ifPresent(sender::send));
    }

    public void process(StockLevelEvent event) {
        levelRules.stream()
            .map(rule -> rule.evaluate(event))
            .forEach(notification -> notification.ifPresent(sender::send));
    }

    public void process(PurchaseOrderPlacedEvent event) {
        orderRules.stream()
            .map(rule -> rule.evaluate(event))
            .forEach(notification -> notification.ifPresent(sender::send));
    }
}
