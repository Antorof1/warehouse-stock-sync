package com.github.antorof1.warehouse.movement;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.warehouse.IntegrationTest;
import com.github.antorof1.warehouse.utils.MovementUtils;
import com.github.antorof1.warehouse.utils.MovementTopicConsumer;
import com.jayway.jsonpath.JsonPath;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static com.github.antorof1.warehouse.utils.MovementUtils.WAREHOUSE_ID;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class MovementEventContractTest {
    @Autowired
    private MovementService movementService;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Test
    void eventIsReadableWithTheInventoryServiceConsumerSettings() {
        String sku = MovementUtils.uniqueSku();

        try (MovementTopicConsumer<MovementEvent> consumer = inventoryConsumer()) {
            Movement saved = movementService.createMovement(sku, WAREHOUSE_ID, 12, MovementReason.RECEIPT);

            MovementEvent event = consumer.awaitRecordFor(sku).value();

            assertThat(event).isEqualTo(new MovementEvent(
                saved.getId(), sku, WAREHOUSE_ID, 12, MovementReason.RECEIPT, saved.getOccurredAt()
            ));
        }
    }

    @Test
    void payloadOnTheWireCarriesEveryFieldAndATypeHeader() {
        String sku = MovementUtils.uniqueSku();

        try (MovementTopicConsumer<String> consumer = rawConsumer()) {
            Movement saved = movementService.createMovement(sku, WAREHOUSE_ID, -4, MovementReason.PICK);

            ConsumerRecord<String, String> record = consumer.awaitRecordFor(sku);

            String payload = record.value();
            assertThat(JsonPath.<String>read(payload, "$.id")).isEqualTo(saved.getId().toString());
            assertThat(JsonPath.<String>read(payload, "$.sku")).isEqualTo(sku);
            assertThat(JsonPath.<String>read(payload, "$.warehouseId")).isEqualTo(WAREHOUSE_ID);
            assertThat(JsonPath.<Integer>read(payload, "$.quantityDelta")).isEqualTo(-4);
            assertThat(JsonPath.<String>read(payload, "$.reason")).isEqualTo("PICK");
            assertThat(Instant.parse(JsonPath.read(payload, "$.occurredAt"))).isEqualTo(saved.getOccurredAt());

            Header typeHeader = record.headers().lastHeader("__TypeId__");
            assertThat(typeHeader).as("consumer resolves the payload type from this header").isNotNull();
            assertThat(new String(typeHeader.value(), StandardCharsets.UTF_8)).isEqualTo(MovementEvent.class.getName());
        }
    }

    @ParameterizedTest
    @EnumSource(MovementReason.class)
    void everyReasonSurvivesTheRoundTrip(MovementReason reason) {
        String sku = MovementUtils.uniqueSku();

        try (MovementTopicConsumer<MovementEvent> consumer = inventoryConsumer()) {
            movementService.createMovement(sku, WAREHOUSE_ID, 1, reason);

            assertThat(consumer.awaitRecordFor(sku).value().reason()).isEqualTo(reason);
        }
    }

    private MovementTopicConsumer<MovementEvent> inventoryConsumer() {
        return MovementTopicConsumer.withInventoryServiceSettings(kafkaContainer.getBootstrapServers());
    }

    private MovementTopicConsumer<String> rawConsumer() {
        return MovementTopicConsumer.withRawPayloads(kafkaContainer.getBootstrapServers());
    }
}
