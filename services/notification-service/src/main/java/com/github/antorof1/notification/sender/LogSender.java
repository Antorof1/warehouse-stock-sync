package com.github.antorof1.notification.sender;

import com.github.antorof1.notification.dispatch.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class LogSender implements NotificationSender {
    private static final Logger log = LoggerFactory.getLogger(LogSender.class);

    @Override
    public void send(Notification notification) {
        log.info(notification.label() + " " + notification.body());
    }
}
