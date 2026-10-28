package com.github.antorof1.simulator.feed;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.Topics;
import com.github.antorof1.simulator.TestcontainersConfiguration;
import com.github.antorof1.simulator.state.PurchaseOrderView;
import com.github.antorof1.simulator.state.SimulationState;
import com.github.antorof1.simulator.state.StockKey;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.github.antorof1.simulator.utils.SimulatorUtils.orderPlaced;
import static com.github.antorof1.simulator.utils.SimulatorUtils.stockLevelEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class KafkaEventListenerIntegrationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String INVENTORY_DLT = Topics.MOVEMENT_EVENTS + ".inventory-service.dlt";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private SimulationState state;

    @Autowired
    private EventFeed feed;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void rebuildsStockLevelsFromStockLevelEvents() {
        StockKey key = uniqueKey();

        send(Topics.STOCK_LEVEL_EVENTS, key.sku(), EventTypes.STOCK_LEVEL_CHANGED,
            jsonMapper.writeValueAsString(stockLevelEvent(key, 0, 7, MovementReason.RECEIPT)));

        await().atMost(TIMEOUT).until(() -> state.level(key).isPresent());
        assertThat(state.level(key)).contains(7);
        assertThat(entryFor(Topics.STOCK_LEVEL_EVENTS, key.sku())).hasValueSatisfying(entry -> {
            assertThat(entry.type()).isEqualTo(EventTypes.STOCK_LEVEL_CHANGED);
            assertThat(entry.payload().get("newLevel").asInt()).isEqualTo(7);
            assertThat(entry.raw()).isNull();
        });
    }

    @Test
    void tracksPurchaseOrdersFromTheirEvents() {
        StockKey key = uniqueKey();

        send(Topics.PURCHASE_ORDER_EVENTS, key.sku(), EventTypes.PURCHASE_ORDER_PLACED,
            jsonMapper.writeValueAsString(orderPlaced(key, 95, Instant.now())));

        await().atMost(TIMEOUT).until(() -> state.openOrder(key).isPresent());
        assertThat(state.openOrder(key)).hasValueSatisfying(order -> {
            assertThat(order.status()).isEqualTo(PurchaseOrderView.Status.OPEN);
            assertThat(order.quantity()).isEqualTo(95);
        });
    }

    @Test
    void showsAnUnreadablePayloadAsRawText() {
        String sku = uniqueKey().sku();

        send(Topics.MOVEMENT_EVENTS, sku, EventTypes.MOVEMENT_RECORDED, "{\"quantityDelta\": oops");

        await().atMost(TIMEOUT).until(() -> entryFor(Topics.MOVEMENT_EVENTS, sku).isPresent());
        assertThat(entryFor(Topics.MOVEMENT_EVENTS, sku)).hasValueSatisfying(entry -> {
            assertThat(entry.payload()).isNull();
            assertThat(entry.raw()).isEqualTo("{\"quantityDelta\": oops");
            assertThat(entry.deadLetter()).isNull();
        });
    }

    @Test
    void recordsDeadLettersWithTheGroupThatGaveUp() {
        String sku = uniqueKey().sku();
        ProducerRecord<String, String> record = new ProducerRecord<>(INVENTORY_DLT, sku, "{\"quantityDelta\": oops");
        record.headers()
            .add(KafkaHeaders.DLT_ORIGINAL_TOPIC, Topics.MOVEMENT_EVENTS.getBytes(StandardCharsets.UTF_8))
            .add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, "Failed to deserialize".getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).join();

        await().atMost(TIMEOUT).until(() -> state.deadLetterTopicsFor(sku).contains(INVENTORY_DLT));
        assertThat(entryFor(INVENTORY_DLT, sku)).hasValueSatisfying(entry -> {
            assertThat(entry.deadLetter().originalTopic()).isEqualTo(Topics.MOVEMENT_EVENTS);
            assertThat(entry.deadLetter().group()).isEqualTo("inventory-service");
            assertThat(entry.deadLetter().exception()).isEqualTo("Failed to deserialize");
        });
    }

    private void send(String topic, String key, String type, String value) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        record.headers().add(EventTypes.HEADER, type.getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).join();
    }

    private Optional<FeedEntry> entryFor(String topic, String key) {
        return feed.recent().stream()
            .filter(entry -> entry.topic().equals(topic) && key.equals(entry.key()))
            .findFirst();
    }

    private static StockKey uniqueKey() {
        return new StockKey("WH-TEST", "SKU-" + UUID.randomUUID());
    }
}
