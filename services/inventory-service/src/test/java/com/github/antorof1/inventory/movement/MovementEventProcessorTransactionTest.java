package com.github.antorof1.inventory.movement;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.inventory.IntegrationTest;
import com.github.antorof1.inventory.idempotency.ProcessedEventRepository;
import com.github.antorof1.inventory.idempotency.ProcessedEventService;
import com.github.antorof1.inventory.stock.StockRepository;
import com.github.antorof1.inventory.utils.StockLevelTopicConsumer;
import com.github.antorof1.inventory.utils.StockUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.UUID;

import static com.github.antorof1.inventory.utils.StockUtils.movementEvent;
import static com.github.antorof1.inventory.utils.StockUtils.stockId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private ProcessedEventService processedEventService;

    @Test
    void commitsTheStockChangeAndTheProcessedMarkerTogether() {
        String sku = StockUtils.uniqueSku();
        MovementEvent event = movementEvent(sku, 12);

        processor.process(event);

        assertThat(stockRepository.findById(stockId(sku)).orElseThrow().getQuantity()).isEqualTo(12);
        assertThat(processedEventRepository.existsById(event.id())).isTrue();
    }

    @Test
    void rollsBackTheStockChangeWhenTheProcessedMarkerFails() {
        String sku = StockUtils.uniqueSku();
        MovementEvent event = movementEvent(sku, 12);
        willThrow(new DataIntegrityViolationException("processed_events is unavailable"))
            .given(processedEventService).markProcessed(any(UUID.class));

        try (StockLevelTopicConsumer<StockLevelEvent> consumer = downstreamConsumer()) {
            assertThatThrownBy(() -> processor.process(event))
                .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(stockRepository.findById(stockId(sku)))
                .as("the stock write shares the transaction with the processed marker")
                .isEmpty();
            assertThat(consumer.findRecordFor(sku, QUIET_WINDOW))
                .as("the publisher only fires after commit, so a rolled back movement stays off the topic")
                .isEmpty();
        }
    }

    private StockLevelTopicConsumer<StockLevelEvent> downstreamConsumer() {
        return StockLevelTopicConsumer.withDownstreamServiceSettings(kafkaContainer.getBootstrapServers());
    }
}
