package com.github.antorof1.reorder.stock;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.reorder.IntegrationTest;
import com.github.antorof1.reorder.idempotency.ProcessedEventRepository;
import com.github.antorof1.reorder.purchaseorder.PurchaseOrder;
import com.github.antorof1.reorder.purchaseorder.PurchaseOrderRepository;
import com.github.antorof1.reorder.purchaseorder.PurchaseOrderStatus;
import com.github.antorof1.reorder.rule.ReorderRuleRepository;
import com.github.antorof1.reorder.utils.PurchaseOrderTopicConsumer;
import com.github.antorof1.reorder.utils.ReorderUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;

import static com.github.antorof1.reorder.utils.ReorderUtils.WAREHOUSE_ID;
import static com.github.antorof1.reorder.utils.ReorderUtils.rule;
import static com.github.antorof1.reorder.utils.ReorderUtils.stockLevelEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

@IntegrationTest
class StockLevelEventProcessorTransactionTest {
    private static final Duration QUIET_WINDOW = Duration.ofSeconds(5);

    @Autowired
    private StockLevelEventProcessor processor;

    @Autowired
    private ReorderRuleRepository reorderRuleRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private KafkaContainer kafkaContainer;

    @MockitoSpyBean
    private PurchaseOrderRepository purchaseOrderRepository;

    @Test
    void commitsTheOrderAndTheProcessedMarkerTogether() {
        String sku = ReorderUtils.uniqueSku();
        reorderRuleRepository.save(rule(sku));
        StockLevelEvent event = stockLevelEvent(sku, 12, 8, MovementReason.PICK);

        processor.process(event);

        assertThat(purchaseOrderRepository.findBySkuAndWarehouseIdAndStatus(sku, WAREHOUSE_ID, PurchaseOrderStatus.OPEN))
            .isPresent();
        assertThat(processedEventRepository.existsById(event.id())).isTrue();
    }

    @Test
    void rollsBackTheProcessedMarkerWhenTheOrderCannotBeSaved() {
        String sku = ReorderUtils.uniqueSku();
        reorderRuleRepository.save(rule(sku));
        StockLevelEvent event = stockLevelEvent(sku, 12, 8, MovementReason.PICK);
        willThrow(new DataIntegrityViolationException("purchase orders are unavailable"))
            .given(purchaseOrderRepository).save(any(PurchaseOrder.class));

        try (PurchaseOrderTopicConsumer<Object> consumer = downstreamConsumer()) {
            assertThatThrownBy(() -> processor.process(event))
                .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(processedEventRepository.existsById(event.id()))
                .as("the marker is claimed up front, so it must roll back with the failed order")
                .isFalse();
            assertThat(purchaseOrderRepository.findBySkuAndWarehouseIdAndStatus(sku, WAREHOUSE_ID, PurchaseOrderStatus.OPEN))
                .isEmpty();
            assertThat(consumer.findRecordFor(sku, QUIET_WINDOW))
                .as("the publisher only fires after commit, so a rolled back order stays off the topic")
                .isEmpty();
        }
    }

    private PurchaseOrderTopicConsumer<Object> downstreamConsumer() {
        return PurchaseOrderTopicConsumer.withDownstreamServiceSettings(kafkaContainer.getBootstrapServers());
    }
}
