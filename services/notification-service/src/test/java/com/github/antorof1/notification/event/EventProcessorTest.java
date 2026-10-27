package com.github.antorof1.notification.event;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.Topics;
import com.github.antorof1.idempotency.ProcessedEventService;
import com.github.antorof1.notification.dispatch.NotificationDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.github.antorof1.notification.utils.NotificationUtils.SKU;
import static com.github.antorof1.notification.utils.NotificationUtils.stockLevelEvent;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EventProcessorTest {
    private final StockLevelEvent event = stockLevelEvent(SKU, 2, -1, MovementReason.PICK);

    @Mock
    private ProcessedEventService processedEventService;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @InjectMocks
    private EventProcessor eventProcessor;

    @Test
    void dispatchesAnEventSeenForTheFirstTime() {
        given(processedEventService.tryMarkProcessed(event.id(), Topics.STOCK_LEVEL_EVENTS)).willReturn(true);

        eventProcessor.handle(event.id(), Topics.STOCK_LEVEL_EVENTS, event);

        verify(notificationDispatcher).process(event);
    }

    @Test
    void skipsAnEventAlreadyProcessed() {
        given(processedEventService.tryMarkProcessed(event.id(), Topics.STOCK_LEVEL_EVENTS)).willReturn(false);

        eventProcessor.handle(event.id(), Topics.STOCK_LEVEL_EVENTS, event);

        verifyNoInteractions(notificationDispatcher);
    }
}
