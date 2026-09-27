package com.github.antorof1.inventory.utils;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.Topics;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class MovementEventProducer {
    public static final String MOVEMENT_RECORDED_TYPE = "warehouse.movement.recorded";

    private static final int SEND_TIMEOUT_SECONDS = 20;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public MovementEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(MovementEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
            Topics.MOVEMENT_EVENTS, event.sku(), objectMapper.writeValueAsString(event)
        );
        record.headers().add("__TypeId__", MOVEMENT_RECORDED_TYPE.getBytes(StandardCharsets.UTF_8));

        try {
            kafkaTemplate.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing " + event, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to publish " + event, e);
        }
    }
}
