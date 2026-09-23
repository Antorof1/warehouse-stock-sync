package com.github.antorof1.notification.utils;

import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class EventProducer {
    private static final int SEND_TIMEOUT_SECONDS = 20;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, event).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing " + event, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to publish " + event, e);
        }
    }
}
