package com.github.antorof1.warehouse.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void poll() {
        for (OutboxEvent event : outboxEventRepository.findPendingForUpdate(100)) {
            send(event).orTimeout(10, TimeUnit.SECONDS).join();
            event.markPublished();
        }
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
