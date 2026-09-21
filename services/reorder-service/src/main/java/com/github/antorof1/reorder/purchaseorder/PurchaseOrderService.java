package com.github.antorof1.reorder.purchaseorder;

import com.fasterxml.uuid.Generators;
import com.github.antorof1.events.purchaseorder.PurchaseOrderExpiredEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class PurchaseOrderService {
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final Duration purchaseOrderTTL;
    private final ApplicationEventPublisher publisher;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                @Value("${app.purchase-orders.ttl}") Duration purchaseOrderTTL,
                                ApplicationEventPublisher publisher) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderTTL = purchaseOrderTTL;
        this.publisher = publisher;
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

                publisher.publishEvent(expiredEvent);
            });
    }
}
