package com.github.antorof1.warehouse.movement;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.warehouse.IntegrationTest;
import com.github.antorof1.warehouse.utils.MovementUtils;
import com.github.antorof1.warehouse.utils.MovementTopicConsumer;
import com.jayway.jsonpath.JsonPath;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.github.antorof1.warehouse.utils.MovementUtils.WAREHOUSE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@IntegrationTest
class MovementApiIntegrationTest {
    private static final Duration SILENCE_WINDOW = Duration.ofSeconds(5);

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MovementRepository movementRepository;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Test
    void persistsMovementAndPublishesEventOnCommit() throws Exception {
        String sku = MovementUtils.uniqueSku();

        try (MovementTopicConsumer<MovementEvent> consumer = subscribedConsumer()) {
            MvcTestResult result = post(sku, 12, "RECEIPT");
            assertThat(result).hasStatus(HttpStatus.CREATED);

            UUID id = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));

            Movement persisted = movementRepository.findById(id).orElseThrow();
            assertThat(persisted.getSku()).isEqualTo(sku);
            assertThat(persisted.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(persisted.getQuantityDelta()).isEqualTo(12);
            assertThat(persisted.getReason()).isEqualTo(MovementReason.RECEIPT);

            ConsumerRecord<String, MovementEvent> record = consumer.awaitRecordFor(sku);
            assertThat(record.key()).as("partition key keeps one SKU's movements ordered").isEqualTo(sku);

            MovementEvent event = record.value();
            assertThat(event.id()).isEqualTo(id);
            assertThat(event.sku()).isEqualTo(sku);
            assertThat(event.warehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(event.quantityDelta()).isEqualTo(12);
            assertThat(event.reason()).isEqualTo(MovementReason.RECEIPT);
            assertThat(event.occurredAt())
                .as("stored timestamp is truncated to the microseconds Postgres keeps")
                .isCloseTo(persisted.getOccurredAt(), within(1000, ChronoUnit.NANOS));
        }
    }

    @Test
    void rejectedRequestTouchesNeitherDatabaseNorTopic() {
        String sku = MovementUtils.uniqueSku();

        try (MovementTopicConsumer<MovementEvent> consumer = subscribedConsumer()) {
            MvcTestResult result = mvc.post()
                .uri("/api/v1/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sku": "%s", "warehouseId": "", "quantityDelta": 12, "reason": "RECEIPT"}""".formatted(sku))
                .exchange();

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(movementRepository.findAll()).noneMatch(movement -> sku.equals(movement.getSku()));
            assertThat(consumer.findRecordFor(sku, SILENCE_WINDOW)).isEmpty();
        }
    }

    private MvcTestResult post(String sku, int quantityDelta, String reason) {
        return mvc.post()
            .uri("/api/v1/movements")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"sku": "%s", "warehouseId": "%s", "quantityDelta": %d, "reason": "%s"}"""
                .formatted(sku, WAREHOUSE_ID, quantityDelta, reason))
            .exchange();
    }

    private MovementTopicConsumer<MovementEvent> subscribedConsumer() {
        return MovementTopicConsumer.withInventoryServiceSettings(kafkaContainer.getBootstrapServers());
    }
}
