package com.github.antorof1.reorder.order;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderSweeper {
    private final OrderService orderService;

    public OrderSweeper(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${app.orders.sweep-interval}")
    public void run() {
        orderService.cleanUpExpiredOrders();
    }
}
