package com.github.antorof1.notification.order;

import com.github.antorof1.events.PurchaseOrderEvent;
import com.github.antorof1.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PurchaseOrderEventListener {
    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderEventListener.class);

    @KafkaListener(topics = Topics.PURCHASE_ORDER_EVENTS)
    public void onPurchaseOrderEvent(PurchaseOrderEvent event) {
        log.info(event.toString());
    }
}
