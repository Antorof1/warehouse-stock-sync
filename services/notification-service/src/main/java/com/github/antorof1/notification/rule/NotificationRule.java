package com.github.antorof1.notification.rule;

import com.github.antorof1.notification.dispatch.Notification;

import java.util.Optional;

public interface NotificationRule<T> {
    Class<T> eventType();

    Optional<Notification> evaluate(T event);

    default Optional<Notification> evaluateIfMatches(Object event) {
        if (event.getClass() != eventType()) return Optional.empty();

        return evaluate(eventType().cast(event));
    }
}
