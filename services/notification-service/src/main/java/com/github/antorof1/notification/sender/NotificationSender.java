package com.github.antorof1.notification.sender;

import com.github.antorof1.notification.dispatch.Notification;

public interface NotificationSender {
    void send(Notification notification);
}
