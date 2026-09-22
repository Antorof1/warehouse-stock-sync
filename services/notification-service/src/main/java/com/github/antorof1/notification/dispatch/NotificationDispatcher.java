package com.github.antorof1.notification.dispatch;

import com.github.antorof1.notification.rule.NotificationRule;
import com.github.antorof1.notification.sender.NotificationSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationDispatcher {
    private final NotificationSender sender;
    private final List<NotificationRule<?>> rules;

    public NotificationDispatcher(NotificationSender sender, List<NotificationRule<?>> rules) {
        this.sender = sender;
        this.rules = rules;
    }

    public void process(Object event) {
        rules.stream()
            .map(rule -> rule.evaluateIfMatches(event))
            .forEach(notification -> notification.ifPresent(sender::send));
    }
}
