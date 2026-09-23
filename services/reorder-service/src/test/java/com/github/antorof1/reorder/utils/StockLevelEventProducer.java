package com.github.antorof1.reorder.utils;

import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.Topics;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class StockLevelEventProducer {
    private static final int SEND_TIMEOUT_SECONDS = 20;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public StockLevelEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(StockLevelEvent event) {
        try {
            kafkaTemplate.send(Topics.STOCK_LEVEL_EVENTS, event.sku(), event)
                .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing " + event, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to publish " + event, e);
        }
    }
}
