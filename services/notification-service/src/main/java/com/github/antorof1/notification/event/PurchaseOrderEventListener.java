package com.github.antorof1.notification.event;

import com.github.antorof1.events.Topics;
import com.github.antorof1.events.purchaseorder.PurchaseOrderCompletedEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderExpiredEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderPlacedEvent;
import com.github.antorof1.notification.dispatch.NotificationDispatcher;
import com.github.antorof1.notification.idempotency.ProcessedEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@KafkaListener(topics = Topics.PURCHASE_ORDER_EVENTS)
public class PurchaseOrderEventListener {
    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderEventListener.class);

    private final ProcessedEventService processedEventService;
    private final NotificationDispatcher dispatcher;

    public PurchaseOrderEventListener(ProcessedEventService processedEventService, NotificationDispatcher dispatcher) {
        this.processedEventService = processedEventService;
        this.dispatcher = dispatcher;
    }

    @KafkaHandler
    public void onPurchaseOrderPlacedEvent(PurchaseOrderPlacedEvent event) {
        if (processedEventService.isProcessed(event.id(), Topics.PURCHASE_ORDER_EVENTS)) return;

        dispatcher.process(event);

        processedEventService.markProcessed(event.id(), Topics.PURCHASE_ORDER_EVENTS);
    }

    @KafkaHandler
    public void onPurchaseOrderCompletedEvent(PurchaseOrderCompletedEvent event) {
        if (processedEventService.isProcessed(event.id(), Topics.PURCHASE_ORDER_EVENTS)) return;

        log.debug("PurchaseOrderCompletedEvent ignored");

        processedEventService.markProcessed(event.id(), Topics.PURCHASE_ORDER_EVENTS);
    }

    @KafkaHandler
    public void onPurchaseOrderExpiredEvent(PurchaseOrderExpiredEvent event) {
        if (processedEventService.isProcessed(event.id(), Topics.PURCHASE_ORDER_EVENTS)) return;

        log.debug("PurchaseOrderExpiredEvent ignored");

        processedEventService.markProcessed(event.id(), Topics.PURCHASE_ORDER_EVENTS);
    }
}
