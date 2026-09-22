package com.github.antorof1.notification.event;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.Topics;
import com.github.antorof1.notification.dispatch.NotificationDispatcher;
import com.github.antorof1.notification.idempotency.ProcessedEventService;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@KafkaListener(topics = Topics.MOVEMENT_EVENTS)
public class MovementEventListener {
    private final ProcessedEventService processedEventService;
    private final NotificationDispatcher dispatcher;

    public MovementEventListener(ProcessedEventService processedEventService, NotificationDispatcher dispatcher) {
        this.processedEventService = processedEventService;
        this.dispatcher = dispatcher;
    }

    @KafkaHandler
    public void onMovementEvent(MovementEvent event) {
        if (!processedEventService.tryMarkProcessed(event.id(), Topics.MOVEMENT_EVENTS)) return;

        dispatcher.process(event);
    }
}
