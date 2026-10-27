package com.github.antorof1.notification.event;

import com.github.antorof1.idempotency.ProcessedEventService;
import com.github.antorof1.notification.dispatch.NotificationDispatcher;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EventProcessor {
    private final ProcessedEventService processedEventService;
    private final NotificationDispatcher notificationDispatcher;

    public EventProcessor(ProcessedEventService processedEventService, NotificationDispatcher notificationDispatcher) {
        this.processedEventService = processedEventService;
        this.notificationDispatcher = notificationDispatcher;
    }

    public void handle(UUID id, String topic, Object event) {
        if (!processedEventService.tryMarkProcessed(id, topic)) return;

        notificationDispatcher.process(event);
    }
}
