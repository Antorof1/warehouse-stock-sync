package com.github.antorof1.warehouse.movement;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MovementEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(MovementEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public MovementEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMovementCreated(MovementEvent event) {
        kafkaTemplate.send(Topics.MOVEMENT_EVENTS, event.sku(), event)
            .whenComplete((result, e) -> {
                if (e == null) return;

                log.error("Failed to publish movement event id={} sku={}", event.id(), event.sku(), e);
            });
    }
}
