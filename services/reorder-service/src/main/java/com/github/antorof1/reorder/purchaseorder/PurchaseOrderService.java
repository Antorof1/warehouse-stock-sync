package com.github.antorof1.reorder.purchaseorder;

import com.fasterxml.uuid.Generators;
import com.github.antorof1.events.Topics;
import com.github.antorof1.events.purchaseorder.PurchaseOrderExpiredEvent;
import com.github.antorof1.outbox.OutboxEventService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class PurchaseOrderService {
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final Duration purchaseOrderTTL;
    private final OutboxEventService outboxEventService;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                @Value("${app.purchase-orders.ttl}") Duration purchaseOrderTTL,
                                OutboxEventService outboxEventService) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderTTL = purchaseOrderTTL;
        this.outboxEventService = outboxEventService;
    }

    @Transactional
    public void cleanUpExpiredOrders() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(purchaseOrderTTL);

        purchaseOrderRepository.expireOpenPurchaseOrdersCreatedBefore(cutoff)
            .forEach(order -> {
                PurchaseOrderExpiredEvent expiredEvent = new PurchaseOrderExpiredEvent(
                    Generators.timeBasedEpochGenerator().generate(),
                    order.getSku(),
                    order.getWarehouseId(),
                    order.getQuantity(),
                    order.getReceivedQuantity(),
                    now
                );

                outboxEventService.record(
                    expiredEvent.id(),
                    Topics.PURCHASE_ORDER_EVENTS,
                    expiredEvent.sku(),
                    expiredEvent
                );
            });
    }
}
