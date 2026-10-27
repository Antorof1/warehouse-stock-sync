package com.github.antorof1.inventory.movement;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.Topics;
import com.github.antorof1.idempotency.ProcessedEventId;
import com.github.antorof1.idempotency.ProcessedEventRepository;
import com.github.antorof1.inventory.IntegrationTest;
import com.github.antorof1.inventory.stock.StockRepository;
import com.github.antorof1.inventory.stock.StockService;
import com.github.antorof1.inventory.utils.StockLevelTopicConsumer;
import com.github.antorof1.inventory.utils.StockUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;

import static com.github.antorof1.inventory.utils.StockUtils.movementEvent;
import static com.github.antorof1.inventory.utils.StockUtils.stockId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;

@IntegrationTest
class MovementEventProcessorTransactionTest {
    private static final Duration QUIET_WINDOW = Duration.ofSeconds(5);

    @Autowired
    private MovementEventProcessor processor;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private KafkaContainer kafkaContainer;

    @MockitoSpyBean
    private StockService stockService;

    @Test
    void commitsTheStockChangeAndTheProcessedMarkerTogether() {
        String sku = StockUtils.uniqueSku();
        MovementEvent event = movementEvent(sku, 12);

        processor.process(event);

        assertThat(stockRepository.findById(stockId(sku)).orElseThrow().getQuantity()).isEqualTo(12);
        assertThat(processedEventRepository.existsById(new ProcessedEventId(event.id(), Topics.MOVEMENT_EVENTS))).isTrue();
    }

    @Test
    void rollsBackTheProcessedMarkerWhenTheStockChangeFails() {
        String sku = StockUtils.uniqueSku();
        MovementEvent event = movementEvent(sku, 12);
        willThrow(new DataIntegrityViolationException("stock is unavailable"))
            .given(stockService).applyMovement(anyString(), anyString(), anyInt(), any(), any());

        try (StockLevelTopicConsumer<StockLevelEvent> consumer = downstreamConsumer()) {
            assertThatThrownBy(() -> processor.process(event))
                .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(processedEventRepository.existsById(new ProcessedEventId(event.id(), Topics.MOVEMENT_EVENTS)))
                .as("the marker is claimed up front, so it must roll back with the failed stock write")
                .isFalse();
            assertThat(stockRepository.findById(stockId(sku))).isEmpty();
            assertThat(consumer.findRecordFor(sku, QUIET_WINDOW))
                .as("the outbox row rolls back with the stock write, so a failed movement stays off the topic")
                .isEmpty();
        }
    }

    private StockLevelTopicConsumer<StockLevelEvent> downstreamConsumer() {
        return StockLevelTopicConsumer.withDownstreamServiceSettings(kafkaContainer.getBootstrapServers());
    }
}
