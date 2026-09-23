package com.github.antorof1.notification.event;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.Topics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.github.antorof1.notification.utils.NotificationUtils.SKU;
import static com.github.antorof1.notification.utils.NotificationUtils.movementEvent;
import static com.github.antorof1.notification.utils.NotificationUtils.placedEvent;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MovementEventListenerTest {
    @Mock
    private EventProcessor eventProcessor;

    @InjectMocks
    private MovementEventListener listener;

    @Test
    void handsAMovementEventToTheProcessorUnderTheMovementTopic() {
        MovementEvent event = movementEvent(SKU, 5, MovementReason.RECEIPT);

        listener.onMovementEvent(event);

        verify(eventProcessor).handle(event.id(), Topics.MOVEMENT_EVENTS, event);
    }

    @Test
    void ignoresAnUnhandledEvent() {
        listener.onUnhandledEvent(placedEvent(SKU, 42));

        verifyNoInteractions(eventProcessor);
    }
}
