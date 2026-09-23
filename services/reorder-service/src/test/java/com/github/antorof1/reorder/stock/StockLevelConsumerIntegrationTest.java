package com.github.antorof1.reorder.stock;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderPlacedEvent;
import com.github.antorof1.reorder.IntegrationTest;
import com.github.antorof1.reorder.idempotency.ProcessedEventRepository;
import com.github.antorof1.reorder.purchaseorder.PurchaseOrder;
import com.github.antorof1.reorder.purchaseorder.PurchaseOrderRepository;
import com.github.antorof1.reorder.purchaseorder.PurchaseOrderStatus;
import com.github.antorof1.reorder.rule.ReorderRuleRepository;
import com.github.antorof1.reorder.utils.PurchaseOrderTopicConsumer;
import com.github.antorof1.reorder.utils.ReorderUtils;
import com.github.antorof1.reorder.utils.StockLevelEventProducer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static com.github.antorof1.reorder.utils.ReorderUtils.WAREHOUSE_ID;
import static com.github.antorof1.reorder.utils.ReorderUtils.rule;
import static com.github.antorof1.reorder.utils.ReorderUtils.stockLevelEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@IntegrationTest
class StockLevelConsumerIntegrationTest {
    private static final Duration QUIET_WINDOW = Duration.ofSeconds(5);

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private ReorderRuleRepository reorderRuleRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private KafkaContainer kafkaContainer;

    private StockLevelEventProducer producer;

    @BeforeEach
    void awaitListenerAssignment() {
        producer = new StockLevelEventProducer(kafkaTemplate);

        await().atMost(Duration.ofSeconds(30)).until(() -> listenerRegistry.getListenerContainers().stream()
            .map(MessageListenerContainer::getAssignedPartitions)
            .allMatch(partitions -> partitions != null && !partitions.isEmpty()));
    }

    @Test
    void placesAnOrderForALowStockEventArrivingOnTheStockLevelTopic() {
        String sku = givenRule();
        StockLevelEvent event = stockLevelEvent(sku, 12, 8, MovementReason.PICK);

        producer.publish(event);

        PurchaseOrder order = awaitOpenOrder(sku);
        assertThat(order.getQuantity()).isEqualTo(42);
        assertThat(processedEventRepository.existsById(event.id())).isTrue();
    }

    @Test
    void publishesThePlacedEventOnThePurchaseOrderTopic() {
        String sku = givenRule();

        try (PurchaseOrderTopicConsumer<Object> consumer = downstreamConsumer()) {
            producer.publish(stockLevelEvent(sku, 12, 8, MovementReason.PICK));

            ConsumerRecord<String, Object> record = consumer.awaitRecordFor(sku);
            assertThat(record.value()).isInstanceOfSatisfying(PurchaseOrderPlacedEvent.class, event -> {
                assertThat(event.sku()).isEqualTo(sku);
                assertThat(event.warehouseId()).isEqualTo(WAREHOUSE_ID);
                assertThat(event.quantity()).isEqualTo(42);
            });
        }
    }

    @Test
    void placesOneOrderForARedeliveredEvent() {
        String sku = givenRule();
        StockLevelEvent event = stockLevelEvent(sku, 12, 8, MovementReason.PICK);

        try (PurchaseOrderTopicConsumer<Object> consumer = downstreamConsumer()) {
            producer.publish(event);
            awaitOpenOrder(sku);

            producer.publish(event);

            List<ConsumerRecord<String, Object>> published = consumer.collectRecordsFor(sku, QUIET_WINDOW);
            assertThat(published).hasSize(1);
            assertThat(purchaseOrderRepository.findAll())
                .filteredOn(order -> order.getSku().equals(sku))
                .hasSize(1);
        }
    }

    private String givenRule() {
        String sku = ReorderUtils.uniqueSku();
        reorderRuleRepository.save(rule(sku));

        return sku;
    }

    private PurchaseOrder awaitOpenOrder(String sku) {
        return await().atMost(Duration.ofSeconds(30))
            .until(() -> openOrderFor(sku), Optional::isPresent)
            .orElseThrow();
    }

    private Optional<PurchaseOrder> openOrderFor(String sku) {
        return purchaseOrderRepository.findBySkuAndWarehouseIdAndStatus(sku, WAREHOUSE_ID, PurchaseOrderStatus.OPEN);
    }

    private PurchaseOrderTopicConsumer<Object> downstreamConsumer() {
        return PurchaseOrderTopicConsumer.withDownstreamServiceSettings(kafkaContainer.getBootstrapServers());
    }
}
