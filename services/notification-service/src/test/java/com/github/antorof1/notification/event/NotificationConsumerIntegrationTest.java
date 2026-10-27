package com.github.antorof1.notification.event;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.Topics;
import com.github.antorof1.idempotency.ProcessedEventId;
import com.github.antorof1.idempotency.ProcessedEventRepository;
import com.github.antorof1.notification.IntegrationTest;
import com.github.antorof1.notification.dispatch.Notification;
import com.github.antorof1.notification.dispatch.NotificationSeverity;
import com.github.antorof1.notification.utils.CapturingSender;
import com.github.antorof1.notification.utils.EventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.time.Duration;

import static com.github.antorof1.notification.utils.NotificationUtils.BULK_THRESHOLD;
import static com.github.antorof1.notification.utils.NotificationUtils.completedEvent;
import static com.github.antorof1.notification.utils.NotificationUtils.expiredEvent;
import static com.github.antorof1.notification.utils.NotificationUtils.movementEvent;
import static com.github.antorof1.notification.utils.NotificationUtils.placedEvent;
import static com.github.antorof1.notification.utils.NotificationUtils.stockLevelEvent;
import static com.github.antorof1.notification.utils.NotificationUtils.uniqueSku;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@IntegrationTest
class NotificationConsumerIntegrationTest {
    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration QUIET_WINDOW = Duration.ofSeconds(5);

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private CapturingSender sender;

    private EventProducer producer;

    private String sku;

    @BeforeEach
    void awaitListenerAssignment() {
        producer = new EventProducer(kafkaTemplate);
        sku = uniqueSku();
        sender.reset();

        await().atMost(DELIVERY_TIMEOUT).until(() -> listenerRegistry.getListenerContainers().stream()
            .map(MessageListenerContainer::getAssignedPartitions)
            .allMatch(partitions -> partitions != null && !partitions.isEmpty()));
    }

    @Test
    void notifiesAMovementEvent() {
        producer.publish(Topics.MOVEMENT_EVENTS, sku, movementEvent(sku, -2, MovementReason.DAMAGE));

        awaitNotification("Damaged stock: " + sku, NotificationSeverity.WARNING);
    }

    @Test
    void notifiesABulkMovementAtTheConfiguredThreshold() {
        producer.publish(Topics.MOVEMENT_EVENTS, sku, movementEvent(sku, BULK_THRESHOLD, MovementReason.RECEIPT));

        awaitNotification("Bulk movement: " + sku, NotificationSeverity.WARNING);
    }

    @Test
    void notifiesAStockLevelEvent() {
        producer.publish(Topics.STOCK_LEVEL_EVENTS, sku, stockLevelEvent(sku, 1, -1, MovementReason.PICK));

        awaitNotification("Negative stock: " + sku, NotificationSeverity.CRITICAL);
    }

    @Test
    void notifiesEachPurchaseOrderEvent() {
        producer.publish(Topics.PURCHASE_ORDER_EVENTS, sku, placedEvent(sku, 42));
        producer.publish(Topics.PURCHASE_ORDER_EVENTS, sku, completedEvent(sku, 42));
        producer.publish(Topics.PURCHASE_ORDER_EVENTS, sku, expiredEvent(sku, 42, 30));

        awaitNotification("Purchase order placed: " + sku, NotificationSeverity.INFO);
        awaitNotification("Purchase order completed: " + sku, NotificationSeverity.INFO);
        awaitNotification("Purchase order expired: " + sku, NotificationSeverity.WARNING);
    }

    @Test
    void notifiesARedeliveredEventOnce() {
        MovementEvent event = movementEvent(sku, -2, MovementReason.DAMAGE);

        producer.publish(Topics.MOVEMENT_EVENTS, sku, event);
        awaitNotification("Damaged stock: " + sku, NotificationSeverity.WARNING);

        producer.publish(Topics.MOVEMENT_EVENTS, sku, event);

        await().during(QUIET_WINDOW).atMost(QUIET_WINDOW.plusSeconds(5))
            .until(() -> sender.attemptsFor(sku).size() == 1);
    }

    @Test
    void marksAnEventProcessedEvenWhenNoRuleMatches() {
        StockLevelEvent event = stockLevelEvent(sku, 5, 3, MovementReason.PICK);

        producer.publish(Topics.STOCK_LEVEL_EVENTS, sku, event);

        ProcessedEventId id = new ProcessedEventId(event.id(), Topics.STOCK_LEVEL_EVENTS);
        await().atMost(DELIVERY_TIMEOUT).until(() -> processedEventRepository.existsById(id));
        assertThat(sender.attemptsFor(sku)).isEmpty();
    }

    @Test
    void ignoresAnEventTypeTheTopicListenerDoesNotHandleAndKeepsConsuming() {
        StockLevelEvent misrouted = stockLevelEvent(sku, 1, -1, MovementReason.PICK);

        producer.publish(Topics.MOVEMENT_EVENTS, sku, misrouted);
        producer.publish(Topics.MOVEMENT_EVENTS, sku, movementEvent(sku, -2, MovementReason.DAMAGE));

        awaitNotification("Damaged stock: " + sku, NotificationSeverity.WARNING);
        assertThat(sender.attemptsFor(sku))
            .extracting(Notification::label)
            .containsExactly("Damaged stock: " + sku);
        assertThat(processedEventRepository.existsById(new ProcessedEventId(misrouted.id(), Topics.MOVEMENT_EVENTS)))
            .isFalse();
    }

    private void awaitNotification(String label, NotificationSeverity severity) {
        await().atMost(DELIVERY_TIMEOUT).until(() -> sender.attemptsFor(sku).stream()
            .anyMatch(notification -> notification.label().equals(label) && notification.severity() == severity));
    }
}
