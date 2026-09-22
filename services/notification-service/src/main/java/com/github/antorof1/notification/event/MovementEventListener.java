package com.github.antorof1.notification.event;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@KafkaListener(topics = Topics.MOVEMENT_EVENTS)
public class MovementEventListener {
    private static final Logger log = LoggerFactory.getLogger(MovementEventListener.class);

    private final EventProcessor eventProcessor;

    public MovementEventListener(EventProcessor eventProcessor) {
        this.eventProcessor = eventProcessor;
    }

    @KafkaHandler
    public void onMovementEvent(MovementEvent event) {
        eventProcessor.handle(event.id(), Topics.MOVEMENT_EVENTS, event);
    }

    @KafkaHandler(isDefault = true)
    public void onUnhandledEvent(Object event) {
        log.debug("No notification handling for {}", event.getClass().getSimpleName());
    }
}
