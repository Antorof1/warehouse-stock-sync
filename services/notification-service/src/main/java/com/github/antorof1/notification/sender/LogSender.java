package com.github.antorof1.notification.sender;

import com.github.antorof1.notification.dispatch.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LogSender implements NotificationSender {
    private static final Logger log = LoggerFactory.getLogger(LogSender.class);

    @Override
    public void send(Notification notification) {
        String message = notification.label() + " " + notification.body();
        switch (notification.severity()) {
            case INFO -> log.info(message);
            case WARNING -> log.warn(message);
            case CRITICAL -> log.error(message);
        }
    }
}
