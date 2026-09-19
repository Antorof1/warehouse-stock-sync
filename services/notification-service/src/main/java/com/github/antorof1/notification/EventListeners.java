package com.github.antorof1.notification;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.PurchaseOrderEvent;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EventListeners {
    @KafkaListener(topics = Topics.MOVEMENT_EVENTS)
    public void onMovementEvent(MovementEvent event) {

    }

    @KafkaListener(topics = Topics.STOCK_LEVEL_EVENTS)
    public void onStockLevelEvent(StockLevelEvent event) {

    }

    @KafkaListener(topics = Topics.PURCHASE_ORDER_EVENTS)
    public void onPurchaseOrderEvent(PurchaseOrderEvent event) {

    }
}
