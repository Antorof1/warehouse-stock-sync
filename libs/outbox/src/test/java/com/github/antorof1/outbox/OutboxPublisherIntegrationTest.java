package com.github.antorof1.outbox;

import com.github.antorof1.outbox.utils.OutboxUtils;
import com.github.antorof1.outbox.utils.TopicConsumer;
import com.jayway.jsonpath.JsonPath;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.github.antorof1.outbox.utils.OutboxUtils.TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@OutboxIntegrationTest
class OutboxPublisherIntegrationTest {
    private static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration SILENCE_WINDOW = Duration.ofSeconds(5);
    private static final Duration OUTAGE = Duration.ofSeconds(3);

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Test
    void publishesCommittedEventWithItsKeyAndTypeHeaderAndMarksItsRow() {
        String key = OutboxUtils.uniqueKey();

        try (TopicConsumer consumer = consumer()) {
            UUID id = recordCommitted(key);

            ConsumerRecord<String, String> record = consumer.awaitRecordFor(key);
            assertThat(JsonPath.<String>read(record.value(), "$.id")).isEqualTo(id.toString());

            Header typeHeader = record.headers().lastHeader("__TypeId__");
            assertThat(typeHeader).as("consumers resolve the payload type from this header").isNotNull();
            assertThat(new String(typeHeader.value(), StandardCharsets.UTF_8)).isEqualTo(TYPE);

            awaitPublished(id);
        }
    }

    @Test
    void doesNotRepublishOnLaterPolls() {
        String key = OutboxUtils.uniqueKey();

        try (TopicConsumer consumer = consumer()) {
            awaitPublished(recordCommitted(key));

            assertThat(consumer.collectRecordsFor(key, SILENCE_WINDOW)).hasSize(1);
        }
    }

    @Test
    void keepsOneKeysEventsInRecordingOrder() {
        String key = OutboxUtils.uniqueKey();

        try (TopicConsumer consumer = consumer()) {
            List<UUID> recorded = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                recorded.add(recordCommitted(key));
            }
            recorded.forEach(this::awaitPublished);

            List<UUID> received = consumer.collectRecordsFor(key, SILENCE_WINDOW).stream()
                .map(record -> UUID.fromString(JsonPath.read(record.value(), "$.id")))
                .toList();
            assertThat(received).containsExactlyElementsOf(recorded);
        }
    }

    @Test
    void holdsEventsWhileKafkaIsDownAndPublishesThemOnceItIsBack() {
        String key = OutboxUtils.uniqueKey();
        var docker = kafkaContainer.getDockerClient();
        String containerId = kafkaContainer.getContainerId();

        try (TopicConsumer consumer = consumer()) {
            UUID id;

            docker.pauseContainerCmd(containerId).exec();
            try {
                id = recordCommitted(key);

                await().during(OUTAGE).atMost(OUTAGE.plusSeconds(2))
                    .until(() -> outboxEventRepository.findById(id).orElseThrow().getPublishedAt() == null);
            } finally {
                docker.unpauseContainerCmd(containerId).exec();
            }

            assertThat(JsonPath.<String>read(consumer.awaitRecordFor(key).value(), "$.id")).isEqualTo(id.toString());
            awaitPublished(id);
        }
    }

    private UUID recordCommitted(String key) {
        UUID id = UUID.randomUUID();

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            outboxEventService.record(id, OutboxUtils.TOPIC, key, OutboxUtils.event(id))
        );

        return id;
    }

    private void awaitPublished(UUID id) {
        await().atMost(PUBLISH_TIMEOUT)
            .until(() -> outboxEventRepository.findById(id).orElseThrow().getPublishedAt() != null);
    }

    private TopicConsumer consumer() {
        return new TopicConsumer(kafkaContainer.getBootstrapServers());
    }
}
