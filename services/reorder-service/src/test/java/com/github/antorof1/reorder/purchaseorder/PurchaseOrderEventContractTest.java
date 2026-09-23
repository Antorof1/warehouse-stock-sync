package com.github.antorof1.reorder.purchaseorder;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.purchaseorder.PurchaseOrderCompletedEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderExpiredEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderPlacedEvent;
import com.github.antorof1.reorder.IntegrationTest;
import com.github.antorof1.reorder.rule.ReorderRuleRepository;
import com.github.antorof1.reorder.stock.StockLevelEventProcessor;
import com.github.antorof1.reorder.utils.PurchaseOrderTopicConsumer;
import com.github.antorof1.reorder.utils.ReorderUtils;
import com.jayway.jsonpath.JsonPath;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.antorof1.reorder.utils.PurchaseOrderTopicConsumer.COMPLETED_TYPE;
import static com.github.antorof1.reorder.utils.PurchaseOrderTopicConsumer.EXPIRED_TYPE;
import static com.github.antorof1.reorder.utils.PurchaseOrderTopicConsumer.PLACED_TYPE;
import static com.github.antorof1.reorder.utils.ReorderUtils.PAST_TTL;
import static com.github.antorof1.reorder.utils.ReorderUtils.WAREHOUSE_ID;
import static com.github.antorof1.reorder.utils.ReorderUtils.ageOrder;
import static com.github.antorof1.reorder.utils.ReorderUtils.rule;
import static com.github.antorof1.reorder.utils.ReorderUtils.stockLevelEvent;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class PurchaseOrderEventContractTest {
    @Autowired
    private StockLevelEventProcessor processor;

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private ReorderRuleRepository reorderRuleRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Test
    void placedEventIsReadableWithTheDownstreamConsumerSettings() {
        String sku = givenRule();

        try (PurchaseOrderTopicConsumer<Object> consumer = downstreamConsumer()) {
            placeOrder(sku);

            assertThat(consumer.awaitRecordFor(sku).value()).isInstanceOfSatisfying(PurchaseOrderPlacedEvent.class, event -> {
                assertThat(event.id()).isNotNull();
                assertThat(event.sku()).isEqualTo(sku);
                assertThat(event.warehouseId()).isEqualTo(WAREHOUSE_ID);
                assertThat(event.quantity()).isEqualTo(42);
                assertThat(event.occurredAt()).isNotNull();
            });
        }
    }

    @Test
    void completedEventIsReadableWithTheDownstreamConsumerSettings() {
        String sku = givenRule();

        try (PurchaseOrderTopicConsumer<Object> consumer = downstreamConsumer()) {
            placeOrder(sku);
            Instant placedAt = openOrderFor(sku).getCreatedAt();
            completeOrder(sku);

            Object value = consumer.awaitRecordsFor(sku, 2).get(1).value();
            assertThat(value).isInstanceOfSatisfying(PurchaseOrderCompletedEvent.class, event -> {
                assertThat(event.id()).isNotNull();
                assertThat(event.sku()).isEqualTo(sku);
                assertThat(event.warehouseId()).isEqualTo(WAREHOUSE_ID);
                assertThat(event.orderedQuantity()).isEqualTo(42);
                assertThat(event.placedAt()).isEqualTo(placedAt);
                assertThat(event.occurredAt()).isAfterOrEqualTo(placedAt);
            });
        }
    }

    @Test
    void expiredEventIsReadableWithTheDownstreamConsumerSettings() {
        String sku = givenRule();

        try (PurchaseOrderTopicConsumer<Object> consumer = downstreamConsumer()) {
            placeOrder(sku);
            processor.process(stockLevelEvent(sku, 8, 20, MovementReason.RECEIPT));
            expireOrder(sku);

            Object value = consumer.awaitRecordsFor(sku, 2).get(1).value();
            assertThat(value).isInstanceOfSatisfying(PurchaseOrderExpiredEvent.class, event -> {
                assertThat(event.id()).isNotNull();
                assertThat(event.sku()).isEqualTo(sku);
                assertThat(event.warehouseId()).isEqualTo(WAREHOUSE_ID);
                assertThat(event.orderedQuantity()).isEqualTo(42);
                assertThat(event.receivedQuantity()).isEqualTo(12);
                assertThat(event.occurredAt()).isNotNull();
            });
        }
    }

    @Test
    void placedPayloadOnTheWireCarriesEveryField() {
        String sku = givenRule();

        try (PurchaseOrderTopicConsumer<String> consumer = rawConsumer()) {
            placeOrder(sku);

            String payload = consumer.awaitRecordFor(sku).value();
            assertThat(UUID.fromString(JsonPath.read(payload, "$.id"))).isNotNull();
            assertThat(JsonPath.<String>read(payload, "$.sku")).isEqualTo(sku);
            assertThat(JsonPath.<String>read(payload, "$.warehouseId")).isEqualTo(WAREHOUSE_ID);
            assertThat(JsonPath.<Integer>read(payload, "$.quantity")).isEqualTo(42);
            assertThat(Instant.parse(JsonPath.read(payload, "$.occurredAt"))).isNotNull();
        }
    }

    @Test
    void everyEventCarriesItsLogicalTypeHeader() {
        String completedSku = givenRule();
        String expiredSku = givenRule();

        try (PurchaseOrderTopicConsumer<String> consumer = rawConsumer()) {
            placeOrder(completedSku);
            completeOrder(completedSku);
            placeOrder(expiredSku);
            expireOrder(expiredSku);

            List<ConsumerRecord<String, String>> completedFlow = consumer.awaitRecordsFor(completedSku, 2);
            assertThat(typeOf(completedFlow.get(0))).isEqualTo(PLACED_TYPE);
            assertThat(typeOf(completedFlow.get(1))).isEqualTo(COMPLETED_TYPE);
            List<ConsumerRecord<String, String>> expiredFlow = consumer.awaitRecordsFor(expiredSku, 2);
            assertThat(typeOf(expiredFlow.get(1))).isEqualTo(EXPIRED_TYPE);
        }
    }

    @Test
    void recordIsKeyedBySkuSoOneSkuKeepsItsOrdering() {
        String sku = givenRule();

        try (PurchaseOrderTopicConsumer<Object> consumer = downstreamConsumer()) {
            placeOrder(sku);

            assertThat(consumer.awaitRecordFor(sku).key()).isEqualTo(sku);
        }
    }

    private String givenRule() {
        String sku = ReorderUtils.uniqueSku();
        reorderRuleRepository.save(rule(sku));

        return sku;
    }

    private void placeOrder(String sku) {
        processor.process(stockLevelEvent(sku, 12, 8, MovementReason.PICK));
    }

    private void completeOrder(String sku) {
        processor.process(stockLevelEvent(sku, 8, 50, MovementReason.RECEIPT));
    }

    private void expireOrder(String sku) {
        ageOrder(jdbcTemplate, openOrderFor(sku).getId(), PAST_TTL);
        purchaseOrderService.cleanUpExpiredOrders();
    }

    private PurchaseOrder openOrderFor(String sku) {
        return purchaseOrderRepository.findBySkuAndWarehouseIdAndStatus(sku, WAREHOUSE_ID, PurchaseOrderStatus.OPEN)
            .orElseThrow();
    }

    private String typeOf(ConsumerRecord<String, String> record) {
        Header typeHeader = record.headers().lastHeader("__TypeId__");
        assertThat(typeHeader).as("consumers resolve the payload type from this header").isNotNull();

        return new String(typeHeader.value(), StandardCharsets.UTF_8);
    }

    private PurchaseOrderTopicConsumer<Object> downstreamConsumer() {
        return PurchaseOrderTopicConsumer.withDownstreamServiceSettings(kafkaContainer.getBootstrapServers());
    }

    private PurchaseOrderTopicConsumer<String> rawConsumer() {
        return PurchaseOrderTopicConsumer.withRawPayloads(kafkaContainer.getBootstrapServers());
    }
}
