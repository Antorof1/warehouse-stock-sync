package com.github.antorof1.simulator.scenario;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.Topics;
import com.github.antorof1.simulator.exception.ScenarioFailedException;
import com.github.antorof1.simulator.feed.EventTypes;
import com.github.antorof1.simulator.feed.KafkaEventListener;
import com.github.antorof1.simulator.state.StockKey;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@Order(3)
public class PoisonPillScenario implements Scenario {
    static final List<String> MOVEMENT_CONSUMER_GROUPS = List.of("inventory-service", "notification-service");
    static final int FOLLOW_UP_RECEIPT = 5;

    private final KafkaTemplate<String, String> kafkaTemplate;

    public PoisonPillScenario(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public String id() {
        return "poison-pill";
    }

    @Override
    public String name() {
        return "Poison pill → DLT";
    }

    @Override
    public String description() {
        return "A malformed record goes straight to each consumer's dead letter topic, "
            + "and the next movement on the same partition is processed normally.";
    }

    @Override
    public void run(ScenarioContext context) throws InterruptedException {
        StockKey key = context.newStockKey("POISON");

        context.step("Publish a malformed %s record for %s directly to %s, bypassing warehouse-service"
            .formatted(EventTypes.MOVEMENT_RECORDED, key.sku(), Topics.MOVEMENT_EVENTS));
        publishPoisonPill(key);
        context.pause();

        context.step("Each consumer fails to deserialize it and dead-letters it without retrying");
        for (String group : MOVEMENT_CONSUMER_GROUPS) {
            String deadLetterTopic = Topics.MOVEMENT_EVENTS + "." + group + KafkaEventListener.DEAD_LETTER_SUFFIX;
            context.await("the record on " + deadLetterTopic,
                () -> context.state().deadLetterTopicsFor(key.sku()).contains(deadLetterTopic));
            context.info("%s parked it on %s".formatted(group, deadLetterTopic));
        }
        context.pause();

        context.step("Record a valid movement for the same SKU, which lands on the same partition");
        context.record(key, FOLLOW_UP_RECEIPT, MovementReason.RECEIPT);
        context.awaitLevel(key, FOLLOW_UP_RECEIPT);
        context.info("The consumers kept going. Inspect the dead letter topics in Kafka UI.");
    }

    private void publishPoisonPill(StockKey key) {
        String payload = "{\"sku\": \"%s\", \"warehouseId\": \"%s\", \"quantityDelta\": NaN-oops"
            .formatted(key.sku(), key.warehouseId());
        ProducerRecord<String, String> record = new ProducerRecord<>(Topics.MOVEMENT_EVENTS, key.sku(), payload);
        record.headers().add(EventTypes.HEADER, EventTypes.MOVEMENT_RECORDED.getBytes(StandardCharsets.UTF_8));

        try {
            kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScenarioFailedException("Interrupted while publishing the poison pill");
        } catch (ExecutionException | TimeoutException e) {
            throw new ScenarioFailedException("Can't publish the poison pill: " + e.getMessage());
        }
    }
}
