package com.github.antorof1.inventory;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MovementEventListener {
    private final MovementEventProcessor processor;

    public MovementEventListener(MovementEventProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(topics = Topics.MOVEMENT_EVENTS)
    public void onMovementEvent(MovementEvent event) {
        processor.process(event);
    }
}
