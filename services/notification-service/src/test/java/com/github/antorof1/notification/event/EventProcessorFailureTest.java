package com.github.antorof1.notification.event;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.Topics;
import com.github.antorof1.notification.IntegrationTest;
import com.github.antorof1.notification.idempotency.ProcessedEventId;
import com.github.antorof1.notification.idempotency.ProcessedEventRepository;
import com.github.antorof1.notification.utils.CapturingSender;
import com.github.antorof1.notification.utils.EventProducer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.time.Duration;

import static com.github.antorof1.notification.utils.NotificationUtils.movementEvent;
import static com.github.antorof1.notification.utils.NotificationUtils.uniqueSku;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@IntegrationTest
class EventProcessorFailureTest {
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

    @BeforeEach
    void awaitListenerAssignment() {
        producer = new EventProducer(kafkaTemplate);
        sender.reset();

        await().atMost(DELIVERY_TIMEOUT).until(() -> listenerRegistry.getListenerContainers().stream()
            .map(MessageListenerContainer::getAssignedPartitions)
            .allMatch(partitions -> partitions != null && !partitions.isEmpty()));
    }

    @AfterEach
    void resetSender() {
        sender.reset();
    }

    @Test
    void dropsTheNotificationWhenTheSenderFailsBecauseTheEventIsAlreadyMarkedProcessed() {
        String sku = uniqueSku();
        MovementEvent event = movementEvent(sku, -2, MovementReason.DAMAGE);
        sender.failWhen(notification -> notification.label().endsWith(sku));

        producer.publish(Topics.MOVEMENT_EVENTS, sku, event);

        await().atMost(DELIVERY_TIMEOUT).until(() -> !sender.attemptsFor(sku).isEmpty());
        await().during(QUIET_WINDOW).atMost(QUIET_WINDOW.plusSeconds(5))
            .alias("the error handler retries the record, but the marker committed before dispatch, so each retry is skipped")
            .until(() -> sender.attemptsFor(sku).size() == 1);
        assertThat(processedEventRepository.existsById(new ProcessedEventId(event.id(), Topics.MOVEMENT_EVENTS)))
            .isTrue();
    }
}
