package com.github.antorof1.reorder.stock;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.Topics;
import com.github.antorof1.idempotency.ProcessedEventId;
import com.github.antorof1.idempotency.ProcessedEventRepository;
import com.github.antorof1.kafkaretry.DeadLetterTopics;
import com.github.antorof1.reorder.IntegrationTest;
import com.github.antorof1.reorder.purchaseorder.PurchaseOrder;
import com.github.antorof1.reorder.purchaseorder.PurchaseOrderRepository;
import com.github.antorof1.reorder.purchaseorder.PurchaseOrderStatus;
import com.github.antorof1.reorder.rule.ReorderRuleRepository;
import com.github.antorof1.reorder.utils.DeadLetterTopicConsumer;
import com.github.antorof1.reorder.utils.ReorderUtils;
import com.github.antorof1.reorder.utils.StockLevelEventProducer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.github.antorof1.reorder.utils.DeadLetterTopicConsumer.header;
import static com.github.antorof1.reorder.utils.ReorderUtils.WAREHOUSE_ID;
import static com.github.antorof1.reorder.utils.ReorderUtils.rule;
import static com.github.antorof1.reorder.utils.ReorderUtils.stockLevelEvent;
import static com.github.antorof1.reorder.utils.StockLevelEventProducer.STOCK_LEVEL_CHANGED_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@IntegrationTest
@TestPropertySource(properties = {
    "app.kafka.retry.max-retries=" + StockLevelDeadLetterIntegrationTest.MAX_RETRIES,
    "app.kafka.retry.initial-interval=100ms"
})
class StockLevelDeadLetterIntegrationTest {
    static final int MAX_RETRIES = 2;

    private static final String DEAD_LETTER_TOPIC = DeadLetterTopics.of(Topics.STOCK_LEVEL_EVENTS, "reorder-service");

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private ReorderRuleRepository reorderRuleRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private KafkaContainer kafkaContainer;

    @MockitoSpyBean
    private PurchaseOrderRepository purchaseOrderRepository;

    private StockLevelEventProducer producer;
    private DeadLetterTopicConsumer deadLetters;

    @BeforeEach
    void awaitListenerAssignment() {
        producer = new StockLevelEventProducer(kafkaTemplate, objectMapper);

        await().atMost(Duration.ofSeconds(30)).until(() -> listenerRegistry.getListenerContainers().stream()
            .map(MessageListenerContainer::getAssignedPartitions)
            .allMatch(partitions -> partitions != null && !partitions.isEmpty()));

        deadLetters = new DeadLetterTopicConsumer(kafkaContainer.getBootstrapServers(), DEAD_LETTER_TOPIC);
    }

    @AfterEach
    void closeDeadLetterConsumer() {
        deadLetters.close();
    }

    @Test
    void deadLettersAStockLevelEventThatKeepsFailingAndRollsBackItsProcessedMarker() {
        String sku = givenRule();
        StockLevelEvent event = stockLevelEvent(sku, 12, 8, MovementReason.PICK);
        willThrow(new DataIntegrityViolationException("purchase orders are unavailable"))
            .given(purchaseOrderRepository).save(argThat((PurchaseOrder order) -> order.getSku().equals(sku)));

        producer.publish(event);

        ConsumerRecord<String, String> deadLetter = deadLetters.awaitRecordFor(sku);
        assertThat(objectMapper.readValue(deadLetter.value(), StockLevelEvent.class)).isEqualTo(event);
        assertThat(header(deadLetter, "__TypeId__")).isEqualTo(STOCK_LEVEL_CHANGED_TYPE);
        assertThat(header(deadLetter, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(Topics.STOCK_LEVEL_EVENTS);
        assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_MESSAGE)).contains("purchase orders are unavailable");
        verify(purchaseOrderRepository, times(MAX_RETRIES + 1))
            .save(argThat((PurchaseOrder order) -> order.getSku().equals(sku)));
        assertThat(processedEventRepository.existsById(new ProcessedEventId(event.id(), Topics.STOCK_LEVEL_EVENTS)))
            .isFalse();
    }

    @Test
    void deadLettersAnUndeserializableStockLevelEventAndKeepsConsuming() {
        String poisonSku = ReorderUtils.uniqueSku();
        String nextSku = givenRule();
        String poison = "{not json";

        publishRaw(poisonSku, poison);
        producer.publish(stockLevelEvent(nextSku, 12, 8, MovementReason.PICK));

        ConsumerRecord<String, String> deadLetter = deadLetters.awaitRecordFor(poisonSku);
        assertThat(deadLetter.value()).isEqualTo(poison);
        assertThat(header(deadLetter, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(Topics.STOCK_LEVEL_EVENTS);
        await().atMost(Duration.ofSeconds(30))
            .until(() -> openOrderFor(nextSku), Optional::isPresent);
    }

    private String givenRule() {
        String sku = ReorderUtils.uniqueSku();
        reorderRuleRepository.save(rule(sku));

        return sku;
    }

    private Optional<PurchaseOrder> openOrderFor(String sku) {
        return purchaseOrderRepository.findBySkuAndWarehouseIdAndStatus(sku, WAREHOUSE_ID, PurchaseOrderStatus.OPEN);
    }

    private void publishRaw(String key, String payload) {
        ProducerRecord<String, String> record = new ProducerRecord<>(Topics.STOCK_LEVEL_EVENTS, key, payload);
        record.headers().add("__TypeId__", STOCK_LEVEL_CHANGED_TYPE.getBytes(StandardCharsets.UTF_8));

        try {
            kafkaTemplate.send(record).get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish " + key, e);
        }
    }
}
