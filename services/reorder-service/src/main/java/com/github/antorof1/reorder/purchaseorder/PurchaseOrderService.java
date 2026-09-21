package com.github.antorof1.reorder.purchaseorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class PurchaseOrderService {
    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderService.class);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final Duration purchaseOrderTTL;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                @Value("${app.purchase-orders.ttl}") Duration purchaseOrderTTL) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderTTL = purchaseOrderTTL;
    }

    @Transactional
    public void cleanUpExpiredOrders() {
        Instant cutoff = Instant.now().minus(purchaseOrderTTL);

        int expired = purchaseOrderRepository.expireOpenPurchaseOrdersCreatedBefore(cutoff);
        if (expired > 0) {
            log.info("Expired {} purchased orders left open since before {}", expired, cutoff);
        }
    }
}
