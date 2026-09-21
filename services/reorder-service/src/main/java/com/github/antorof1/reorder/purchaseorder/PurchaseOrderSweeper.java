package com.github.antorof1.reorder.purchaseorder;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PurchaseOrderSweeper {
    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderSweeper(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @Scheduled(fixedDelayString = "${app.purchase-orders.sweep-interval}")
    public void run() {
        purchaseOrderService.cleanUpExpiredOrders();
    }
}
