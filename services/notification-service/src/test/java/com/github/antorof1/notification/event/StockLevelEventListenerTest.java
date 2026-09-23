package com.github.antorof1.notification.event;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.Topics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.github.antorof1.notification.utils.NotificationUtils.SKU;
import static com.github.antorof1.notification.utils.NotificationUtils.movementEvent;
import static com.github.antorof1.notification.utils.NotificationUtils.stockLevelEvent;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StockLevelEventListenerTest {
    @Mock
    private EventProcessor eventProcessor;

    @InjectMocks
    private StockLevelEventListener listener;

    @Test
    void handsAStockLevelEventToTheProcessorUnderTheStockLevelTopic() {
        StockLevelEvent event = stockLevelEvent(SKU, 5, 3, MovementReason.PICK);

        listener.onStockLevelEvent(event);

        verify(eventProcessor).handle(event.id(), Topics.STOCK_LEVEL_EVENTS, event);
    }

    @Test
    void ignoresAnUnhandledEvent() {
        listener.onUnhandledEvent(movementEvent(SKU, 5, MovementReason.PICK));

        verifyNoInteractions(eventProcessor);
    }
}
