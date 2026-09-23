package com.github.antorof1.notification.event;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.Topics;
import com.github.antorof1.events.purchaseorder.PurchaseOrderCompletedEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderExpiredEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderPlacedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.github.antorof1.notification.utils.NotificationUtils.SKU;
import static com.github.antorof1.notification.utils.NotificationUtils.completedEvent;
import static com.github.antorof1.notification.utils.NotificationUtils.expiredEvent;
import static com.github.antorof1.notification.utils.NotificationUtils.placedEvent;
import static com.github.antorof1.notification.utils.NotificationUtils.stockLevelEvent;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderEventListenerTest {
    @Mock
    private EventProcessor eventProcessor;

    @InjectMocks
    private PurchaseOrderEventListener listener;

    @Test
    void handsAPlacedEventToTheProcessor() {
        PurchaseOrderPlacedEvent event = placedEvent(SKU, 42);

        listener.onPurchaseOrderPlacedEvent(event);

        verify(eventProcessor).handle(event.id(), Topics.PURCHASE_ORDER_EVENTS, event);
    }

    @Test
    void handsACompletedEventToTheProcessor() {
        PurchaseOrderCompletedEvent event = completedEvent(SKU, 42);

        listener.onPurchaseOrderCompletedEvent(event);

        verify(eventProcessor).handle(event.id(), Topics.PURCHASE_ORDER_EVENTS, event);
    }

    @Test
    void handsAnExpiredEventToTheProcessor() {
        PurchaseOrderExpiredEvent event = expiredEvent(SKU, 42, 30);

        listener.onPurchaseOrderExpiredEvent(event);

        verify(eventProcessor).handle(event.id(), Topics.PURCHASE_ORDER_EVENTS, event);
    }

    @Test
    void ignoresAnUnhandledEvent() {
        listener.onUnhandledEvent(stockLevelEvent(SKU, 5, 3, MovementReason.PICK));

        verifyNoInteractions(eventProcessor);
    }
}
