package com.github.antorof1.warehouse.outbox;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.warehouse.IntegrationTest;
import com.github.antorof1.warehouse.movement.Movement;
import com.github.antorof1.warehouse.movement.MovementService;
import com.github.antorof1.warehouse.utils.MovementTopicConsumer;
import com.github.antorof1.warehouse.utils.MovementUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.github.antorof1.warehouse.utils.MovementUtils.WAREHOUSE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@IntegrationTest
class OutboxPublisherIntegrationTest {
    private static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration SILENCE_WINDOW = Duration.ofSeconds(5);
    private static final Duration OUTAGE = Duration.ofSeconds(3);

    @Autowired
    private MovementService movementService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Test
    void publishesCommittedEventAndMarksItsRow() {
        String sku = MovementUtils.uniqueSku();

        try (MovementTopicConsumer<MovementEvent> consumer = consumer()) {
            Movement saved = movementService.createMovement(sku, WAREHOUSE_ID, 12, MovementReason.RECEIPT);

            assertThat(consumer.awaitRecordFor(sku).value().id()).isEqualTo(saved.getId());
            awaitPublished(saved.getId());
        }
    }

    @Test
    void doesNotRepublishOnLaterPolls() {
        String sku = MovementUtils.uniqueSku();

        try (MovementTopicConsumer<MovementEvent> consumer = consumer()) {
            Movement saved = movementService.createMovement(sku, WAREHOUSE_ID, 12, MovementReason.RECEIPT);
            awaitPublished(saved.getId());

            // the poller runs every 500 ms, so this window spans many polls
            assertThat(consumer.collectRecordsFor(sku, SILENCE_WINDOW)).hasSize(1);
        }
    }

    @Test
    void keepsOneSkusEventsInCreationOrder() {
        String sku = MovementUtils.uniqueSku();

        try (MovementTopicConsumer<MovementEvent> consumer = consumer()) {
            List<UUID> created = new ArrayList<>();
            for (int delta = 1; delta <= 5; delta++) {
                created.add(movementService.createMovement(sku, WAREHOUSE_ID, delta, MovementReason.RECEIPT).getId());
            }
            created.forEach(this::awaitPublished);

            List<UUID> received = consumer.collectRecordsFor(sku, SILENCE_WINDOW).stream()
                .map(ConsumerRecord::value)
                .map(MovementEvent::id)
                .toList();
            assertThat(received).containsExactlyElementsOf(created);
        }
    }

    @Test
    void holdsEventsWhileKafkaIsDownAndPublishesThemOnceItIsBack() {
        String sku = MovementUtils.uniqueSku();
        var docker = kafkaContainer.getDockerClient();
        String containerId = kafkaContainer.getContainerId();

        try (MovementTopicConsumer<MovementEvent> consumer = consumer()) {
            UUID id;

            docker.pauseContainerCmd(containerId).exec();
            try {
                id = movementService.createMovement(sku, WAREHOUSE_ID, 12, MovementReason.RECEIPT).getId();

                await().during(OUTAGE).atMost(OUTAGE.plusSeconds(2))
                    .until(() -> outboxEventRepository.findById(id).orElseThrow().getPublishedAt() == null);
            } finally {
                docker.unpauseContainerCmd(containerId).exec();
            }

            assertThat(consumer.awaitRecordFor(sku).value().id()).isEqualTo(id);
            awaitPublished(id);
        }
    }

    private void awaitPublished(UUID id) {
        await().atMost(PUBLISH_TIMEOUT)
            .until(() -> outboxEventRepository.findById(id).orElseThrow().getPublishedAt() != null);
    }

    private MovementTopicConsumer<MovementEvent> consumer() {
        return MovementTopicConsumer.withInventoryServiceSettings(kafkaContainer.getBootstrapServers());
    }
}
