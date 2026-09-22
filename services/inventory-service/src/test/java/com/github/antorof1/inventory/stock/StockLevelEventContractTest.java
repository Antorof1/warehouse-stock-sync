package com.github.antorof1.inventory.stock;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.inventory.IntegrationTest;
import com.github.antorof1.inventory.utils.StockLevelTopicConsumer;
import com.github.antorof1.inventory.utils.StockUtils;
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
import java.util.UUID;

import static com.github.antorof1.inventory.utils.StockUtils.OCCURRED_AT;
import static com.github.antorof1.inventory.utils.StockUtils.WAREHOUSE_ID;
import static com.github.antorof1.inventory.utils.StockUtils.newEventId;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class StockLevelEventContractTest {
    @Autowired
    private StockService stockService;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Test
    void eventIsReadableWithTheDownstreamConsumerSettings() {
        String sku = StockUtils.uniqueSku();

        try (StockLevelTopicConsumer<StockLevelEvent> consumer = downstreamConsumer()) {
            applyMovement(sku, 12, MovementReason.RECEIPT);

            StockLevelEvent event = consumer.awaitRecordFor(sku).value();

            assertThat(event.id()).isNotNull();
            assertThat(event.sku()).isEqualTo(sku);
            assertThat(event.warehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(event.previousLevel()).isZero();
            assertThat(event.newLevel()).isEqualTo(12);
            assertThat(event.reason()).isEqualTo(MovementReason.RECEIPT);
            assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
        }
    }

    @Test
    void payloadOnTheWireCarriesEveryFieldAndATypeHeader() {
        String sku = StockUtils.uniqueSku();

        try (StockLevelTopicConsumer<String> consumer = rawConsumer()) {
            applyMovement(sku, 12, MovementReason.RECEIPT);
            applyMovement(sku, -4, MovementReason.PICK);

            ConsumerRecord<String, String> record = consumer.awaitRecordsFor(sku, 2).get(1);

            String payload = record.value();
            assertThat(UUID.fromString(JsonPath.read(payload, "$.id"))).isNotNull();
            assertThat(JsonPath.<String>read(payload, "$.sku")).isEqualTo(sku);
            assertThat(JsonPath.<String>read(payload, "$.warehouseId")).isEqualTo(WAREHOUSE_ID);
            assertThat(JsonPath.<Integer>read(payload, "$.newLevel")).isEqualTo(8);
            assertThat(JsonPath.<Integer>read(payload, "$.previousLevel")).isEqualTo(12);
            assertThat(JsonPath.<String>read(payload, "$.reason")).isEqualTo("PICK");
            assertThat(Instant.parse(JsonPath.read(payload, "$.occurredAt"))).isEqualTo(OCCURRED_AT);

            Header typeHeader = record.headers().lastHeader("__TypeId__");
            assertThat(typeHeader).as("consumers resolve the payload type from this header").isNotNull();
            assertThat(new String(typeHeader.value(), StandardCharsets.UTF_8))
                .as("logical name, not the Java class, travels on the wire")
                .isEqualTo(StockLevelTopicConsumer.STOCK_LEVEL_CHANGED_TYPE);
        }
    }

    @Test
    void recordIsKeyedBySkuSoOneSkuKeepsItsOrdering() {
        String sku = StockUtils.uniqueSku();

        try (StockLevelTopicConsumer<StockLevelEvent> consumer = downstreamConsumer()) {
            applyMovement(sku, 12, MovementReason.RECEIPT);

            assertThat(consumer.awaitRecordFor(sku).key()).isEqualTo(sku);
        }
    }

    @ParameterizedTest
    @EnumSource(MovementReason.class)
    void everyReasonSurvivesTheRoundTrip(MovementReason reason) {
        String sku = StockUtils.uniqueSku();

        try (StockLevelTopicConsumer<StockLevelEvent> consumer = downstreamConsumer()) {
            applyMovement(sku, 1, reason);

            assertThat(consumer.awaitRecordFor(sku).value().reason()).isEqualTo(reason);
        }
    }

    private void applyMovement(String sku, int quantityDelta, MovementReason reason) {
        stockService.applyMovement(sku, WAREHOUSE_ID, quantityDelta, reason, OCCURRED_AT);
    }

    private StockLevelTopicConsumer<StockLevelEvent> downstreamConsumer() {
        return StockLevelTopicConsumer.withDownstreamServiceSettings(kafkaContainer.getBootstrapServers());
    }

    private StockLevelTopicConsumer<String> rawConsumer() {
        return StockLevelTopicConsumer.withRawPayloads(kafkaContainer.getBootstrapServers());
    }
}
