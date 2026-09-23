package com.github.antorof1.notification.utils;

import com.github.antorof1.notification.dispatch.Notification;
import com.github.antorof1.notification.sender.NotificationSender;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

public final class CapturingSender implements NotificationSender {
    private final List<Notification> attempts = new CopyOnWriteArrayList<>();
    private volatile Predicate<Notification> failWhen = notification -> false;

    @Override
    public void send(Notification notification) {
        attempts.add(notification);
        if (failWhen.test(notification)) throw new IllegalStateException("Sender down for " + notification.label());
    }

    public List<Notification> attemptsFor(String sku) {
        return attempts.stream()
            .filter(notification -> notification.label().endsWith(sku))
            .toList();
    }

    public void failWhen(Predicate<Notification> predicate) {
        failWhen = predicate;
    }

    public void reset() {
        attempts.clear();
        failWhen = notification -> false;
    }
}
