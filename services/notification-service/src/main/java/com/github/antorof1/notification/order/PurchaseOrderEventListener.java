package com.github.antorof1.notification.order;

import com.github.antorof1.events.PurchaseOrderEvent;
import com.github.antorof1.events.Topics;
import com.github.antorof1.notification.idempotency.ProcessedEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PurchaseOrderEventListener {
    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderEventListener.class);

    private final ProcessedEventService processedEventService;

    public PurchaseOrderEventListener(ProcessedEventService processedEventService) {
        this.processedEventService = processedEventService;
    }

    @KafkaListener(topics = Topics.PURCHASE_ORDER_EVENTS)
    public void onPurchaseOrderEvent(PurchaseOrderEvent event) {
        if (processedEventService.isProcessed(event.id())) return;

        log.info(event.toString());

        processedEventService.markProcessed(event.id());
    }
}
