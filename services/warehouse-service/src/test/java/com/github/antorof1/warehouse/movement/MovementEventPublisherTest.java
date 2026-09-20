package com.github.antorof1.warehouse.movement;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.Topics;
import com.github.antorof1.warehouse.utils.MovementUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MovementEventPublisherTest {
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private MovementEventPublisher movementEventPublisher;

    @Test
    void sendsEventToMovementTopicKeyedBySku() {
        given(kafkaTemplate.send(anyString(), anyString(), any())).willReturn(CompletableFuture.completedFuture(null));
        MovementEvent event = MovementUtils.event();

        movementEventPublisher.onMovementCreated(event);

        verify(kafkaTemplate).send(Topics.MOVEMENT_EVENTS, event.sku(), event);
    }

    @Test
    void doesNotPropagateSendFailures() {
        given(kafkaTemplate.send(anyString(), anyString(), any()))
            .willReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        assertThatCode(() -> movementEventPublisher.onMovementCreated(MovementUtils.event()))
            .doesNotThrowAnyException();
    }
}
