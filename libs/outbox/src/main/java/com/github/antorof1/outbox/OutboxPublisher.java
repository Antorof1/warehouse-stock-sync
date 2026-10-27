package com.github.antorof1.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
    static final String PUBLISHED = "outbox.events.published";
    static final String FAILED = "outbox.events.failed";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;
    private final MeterRegistry meterRegistry;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, String> kafkaTemplate,
                           OutboxProperties outboxProperties, MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = outboxProperties.batchSize();
        this.meterRegistry = meterRegistry;
    }

    @SchedulerLock(name = "outbox-publisher")
    public void poll() {
        List<OutboxEvent> batch = outboxEventRepository.findPending(batchSize);
        if (batch.isEmpty()) return;

        Map<OutboxEvent, CompletableFuture<SendResult<String, String>>> sends = startSends(batch);
        List<UUID> published = awaitSends(sends);

        if (!published.isEmpty()) {
            outboxEventRepository.markPublished(published);
        }
    }

    private Map<OutboxEvent, CompletableFuture<SendResult<String, String>>> startSends(List<OutboxEvent> events) {
        Map<OutboxEvent, CompletableFuture<SendResult<String, String>>> sends = new LinkedHashMap<>();

        for (OutboxEvent event : events) {
            try {
                sends.put(event, send(event));
            } catch (KafkaException e) {
                count(FAILED, event);
                if (e.getCause() instanceof RetriableException) {
                    log.warn("Kafka unavailable: {}", e.getCause().getMessage());
                    break;
                }
                log.error("Outbox event {} can't be sent", event.getId(), e);
            }
        }

        return sends;
    }

    private List<UUID> awaitSends(Map<OutboxEvent, CompletableFuture<SendResult<String, String>>> sends) {
        List<UUID> published = new ArrayList<>();

        sends.forEach((event, future) -> {
            try {
                future.join();
                published.add(event.getId());
                count(PUBLISHED, event);
            } catch (CompletionException e) {
                count(FAILED, event);
                log.warn("Failed to publish outbox event {}: {}", event.getId(), e.getCause().getMessage());
            }
        });

        return published;
    }

    private void count(String name, OutboxEvent event) {
        Counter.builder(name)
            .tag("topic", event.getTopic())
            .register(meterRegistry)
            .increment();
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
