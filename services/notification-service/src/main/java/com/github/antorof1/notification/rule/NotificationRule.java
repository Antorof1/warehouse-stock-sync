package com.github.antorof1.notification.rule;

import com.github.antorof1.notification.dispatch.Notification;

import java.util.Optional;

public interface NotificationRule<T> {
    Optional<Notification> evaluate(T event);
}
