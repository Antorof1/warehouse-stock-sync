package com.github.antorof1.reorder.utils;

import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.Topics;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class StockLevelEventProducer {
    public static final String STOCK_LEVEL_CHANGED_TYPE = "inventory.stock-level.changed";

    private static final int SEND_TIMEOUT_SECONDS = 20;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public StockLevelEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(StockLevelEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
            Topics.STOCK_LEVEL_EVENTS, event.sku(), objectMapper.writeValueAsString(event)
        );
        record.headers().add("__TypeId__", STOCK_LEVEL_CHANGED_TYPE.getBytes(StandardCharsets.UTF_8));

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
