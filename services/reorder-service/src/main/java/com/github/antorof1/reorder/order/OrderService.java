package com.github.antorof1.reorder.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final Duration orderTTL;

    public OrderService(OrderRepository orderRepository, @Value("${app.orders.ttl}") Duration orderTTL) {
        this.orderRepository = orderRepository;
        this.orderTTL = orderTTL;
    }

    @Transactional
    public void cleanUpExpiredOrders() {
        Instant cutoff = Instant.now().minus(orderTTL);

        int expired = orderRepository.expireOpenOrdersCreatedBefore(cutoff);
        if (expired > 0) {
            log.info("Expired {} orders left open since before {}", expired, cutoff);
        }
    }
}
