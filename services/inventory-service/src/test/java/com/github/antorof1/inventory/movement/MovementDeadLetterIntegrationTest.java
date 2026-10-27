package com.github.antorof1.inventory.movement;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.Topics;
import com.github.antorof1.idempotency.ProcessedEventId;
import com.github.antorof1.idempotency.ProcessedEventRepository;
import com.github.antorof1.inventory.IntegrationTest;
import com.github.antorof1.inventory.stock.StockRepository;
import com.github.antorof1.inventory.stock.StockService;
import com.github.antorof1.inventory.utils.DeadLetterTopicConsumer;
import com.github.antorof1.inventory.utils.MovementEventProducer;
import com.github.antorof1.inventory.utils.StockUtils;
import com.github.antorof1.kafkaretry.DeadLetterTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
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
import java.util.concurrent.TimeUnit;

import static com.github.antorof1.inventory.utils.DeadLetterTopicConsumer.header;
import static com.github.antorof1.inventory.utils.MovementEventProducer.MOVEMENT_RECORDED_TYPE;
import static com.github.antorof1.inventory.utils.StockUtils.movementEvent;
import static com.github.antorof1.inventory.utils.StockUtils.stockId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@IntegrationTest
@TestPropertySource(properties = {
    "app.kafka.retry.max-retries=" + MovementDeadLetterIntegrationTest.MAX_RETRIES,
    "app.kafka.retry.initial-interval=100ms"
})
class MovementDeadLetterIntegrationTest {
    static final int MAX_RETRIES = 2;

    private static final String DEAD_LETTER_TOPIC = DeadLetterTopics.of(Topics.MOVEMENT_EVENTS, "inventory-service");

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

    @MockitoSpyBean
    private StockService stockService;

    private MovementEventProducer producer;
    private DeadLetterTopicConsumer deadLetters;

    @BeforeEach
    void awaitListenerAssignment() {
        producer = new MovementEventProducer(kafkaTemplate, objectMapper);

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
    void deadLettersAMovementThatKeepsFailingAndRollsBackItsProcessedMarker() {
        String sku = StockUtils.uniqueSku();
        MovementEvent event = movementEvent(sku, 12);
        willThrow(new DataAccessResourceFailureException("stock is unavailable"))
            .given(stockService).applyMovement(eq(sku), anyString(), anyInt(), any(), any());

        producer.publish(event);

        ConsumerRecord<String, String> deadLetter = deadLetters.awaitRecordFor(sku);
        assertThat(objectMapper.readValue(deadLetter.value(), MovementEvent.class)).isEqualTo(event);
        assertThat(header(deadLetter, "__TypeId__")).isEqualTo(MOVEMENT_RECORDED_TYPE);
        assertThat(header(deadLetter, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(Topics.MOVEMENT_EVENTS);
        assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_MESSAGE)).contains("stock is unavailable");
        verify(stockService, times(MAX_RETRIES + 1)).applyMovement(eq(sku), anyString(), anyInt(), any(), any());
        assertThat(processedEventRepository.existsById(new ProcessedEventId(event.id(), Topics.MOVEMENT_EVENTS)))
            .isFalse();
        assertThat(stockRepository.findById(stockId(sku))).isEmpty();
    }

    @Test
    void deadLettersAnUndeserializableMovementAndKeepsConsuming() {
        String poisonSku = StockUtils.uniqueSku();
        String nextSku = StockUtils.uniqueSku();
        String poison = "{not json";

        publishRaw(poisonSku, poison);
        producer.publish(movementEvent(nextSku, 7));

        ConsumerRecord<String, String> deadLetter = deadLetters.awaitRecordFor(poisonSku);
        assertThat(deadLetter.value()).isEqualTo(poison);
        assertThat(header(deadLetter, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(Topics.MOVEMENT_EVENTS);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(stockRepository.findById(stockId(nextSku))).hasValueSatisfying(stock ->
                assertThat(stock.getQuantity()).isEqualTo(7)));
    }

    private void publishRaw(String key, String payload) {
        ProducerRecord<String, String> record = new ProducerRecord<>(Topics.MOVEMENT_EVENTS, key, payload);
        record.headers().add("__TypeId__", MOVEMENT_RECORDED_TYPE.getBytes(StandardCharsets.UTF_8));

        try {
            kafkaTemplate.send(record).get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish " + key, e);
        }
    }
}
