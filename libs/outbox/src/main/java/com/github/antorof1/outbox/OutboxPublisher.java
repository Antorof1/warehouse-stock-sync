package com.github.antorof1.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.RetriableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, String> kafkaTemplate,
                           OutboxProperties outboxProperties) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = outboxProperties.batchSize();
    }

    public void poll() {
        List<OutboxEvent> batch = outboxEventRepository.findPending(batchSize);
        if (batch.isEmpty()) return;

        Map<UUID, CompletableFuture<SendResult<String, String>>> sends = startSends(batch);
        List<UUID> published = awaitSends(sends);

        if (!published.isEmpty()) {
            outboxEventRepository.markPublished(published);
        }
    }

    private Map<UUID, CompletableFuture<SendResult<String, String>>> startSends(List<OutboxEvent> events) {
        Map<UUID, CompletableFuture<SendResult<String, String>>> sends = new LinkedHashMap<>();

        for (OutboxEvent event : events) {
            try {
                sends.put(event.getId(), send(event));
            } catch (KafkaException e) {
                if (e.getCause() instanceof RetriableException) {
                    log.warn("Kafka unavailable: {}", e.getCause().getMessage());
                    break;
                }
                log.error("Outbox event {} can't be sent", event.getId(), e);
            }
        }

        return sends;
    }

    private List<UUID> awaitSends(Map<UUID, CompletableFuture<SendResult<String, String>>> sends) {
        List<UUID> published = new ArrayList<>();

        sends.forEach((id, future) -> {
            try {
                future.join();
                published.add(id);
            } catch (CompletionException e) {
                log.warn("Failed to publish outbox event {}: {}", id, e.getCause().getMessage());
            }
        });

        return published;
    }

    private CompletableFuture<SendResult<String, String>> send(OutboxEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
            event.getTopic(),
            event.getKey(),
            event.getPayload()
        );

        record.headers().add("__TypeId__", event.getType().getBytes(StandardCharsets.UTF_8));

        return kafkaTemplate.send(record);
    }
}
