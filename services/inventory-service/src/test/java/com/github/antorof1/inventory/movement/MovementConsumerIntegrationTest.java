package com.github.antorof1.inventory.movement;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.Topics;
import com.github.antorof1.idempotency.ProcessedEventId;
import com.github.antorof1.idempotency.ProcessedEventRepository;
import com.github.antorof1.inventory.IntegrationTest;
import com.github.antorof1.inventory.stock.StockRepository;
import com.github.antorof1.inventory.utils.MovementEventProducer;
import com.github.antorof1.inventory.utils.StockLevelTopicConsumer;
import com.github.antorof1.inventory.utils.StockUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

import static com.github.antorof1.inventory.utils.StockUtils.WAREHOUSE_ID;
import static com.github.antorof1.inventory.utils.StockUtils.movementEvent;
import static com.github.antorof1.inventory.utils.StockUtils.stockId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@IntegrationTest
class MovementConsumerIntegrationTest {
    private static final Duration QUIET_WINDOW = Duration.ofSeconds(5);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private KafkaContainer kafkaContainer;

    private MovementEventProducer producer;

    @BeforeEach
    void awaitListenerAssignment() {
        producer = new MovementEventProducer(kafkaTemplate, objectMapper);

        await().atMost(Duration.ofSeconds(30)).until(() -> listenerRegistry.getListenerContainers().stream()
            .map(MessageListenerContainer::getAssignedPartitions)
            .allMatch(partitions -> partitions != null && !partitions.isEmpty()));
    }

    @Test
    void appliesAMovementArrivingOnTheMovementTopic() {
        String sku = StockUtils.uniqueSku();

        producer.publish(movementEvent(sku, 12));

        awaitQuantity(sku, 12);
    }

    @Test
    void accumulatesSuccessiveMovementsForTheSameSku() {
        String sku = StockUtils.uniqueSku();

        producer.publish(movementEvent(sku, 5));
        awaitQuantity(sku, 5);
        producer.publish(movementEvent(sku, -2, MovementReason.PICK));

        awaitQuantity(sku, 3);
    }

    @Test
    void marksTheEventProcessed() {
        String sku = StockUtils.uniqueSku();
        MovementEvent event = movementEvent(sku, 4);

        producer.publish(event);

        awaitQuantity(sku, 4);
        assertThat(processedEventRepository.existsById(new ProcessedEventId(event.id(), Topics.MOVEMENT_EVENTS))).isTrue();
    }

    @Test
    void publishesTheResultingStockLevelEvent() {
        String sku = StockUtils.uniqueSku();

        try (StockLevelTopicConsumer<StockLevelEvent> consumer = downstreamConsumer()) {
            producer.publish(movementEvent(sku, 12));

            StockLevelEvent event = consumer.awaitRecordFor(sku).value();
            assertThat(event.sku()).isEqualTo(sku);
            assertThat(event.warehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(event.previousLevel()).isZero();
            assertThat(event.newLevel()).isEqualTo(12);
            assertThat(event.reason()).isEqualTo(MovementReason.RECEIPT);
            assertThat(event.occurredAt()).isEqualTo(StockUtils.OCCURRED_AT);
        }
    }

    @Test
    void appliesARedeliveredEventOnlyOnce() {
        String sku = StockUtils.uniqueSku();
        MovementEvent event = movementEvent(sku, 12);

        try (StockLevelTopicConsumer<StockLevelEvent> consumer = downstreamConsumer()) {
            producer.publish(event);
            awaitQuantity(sku, 12);

            producer.publish(event);

            List<ConsumerRecord<String, StockLevelEvent>> published = consumer.collectRecordsFor(sku, QUIET_WINDOW);
            assertThat(published).hasSize(1);
            assertThat(quantityOf(sku)).isEqualTo(12);
        }
    }

    private StockLevelTopicConsumer<StockLevelEvent> downstreamConsumer() {
        return StockLevelTopicConsumer.withDownstreamServiceSettings(kafkaContainer.getBootstrapServers());
    }

    private void awaitQuantity(String sku, int expected) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(quantityOf(sku)).isEqualTo(expected));
    }

    private Integer quantityOf(String sku) {
        return stockRepository.findById(stockId(sku)).map(stock -> stock.getQuantity()).orElse(null);
    }
}
