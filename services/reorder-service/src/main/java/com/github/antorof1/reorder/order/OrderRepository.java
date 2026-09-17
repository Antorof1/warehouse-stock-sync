package com.github.antorof1.reorder.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findBySkuAndWarehouseIdAndStatus(String sku, String warehouseId, OrderStatus status);
}
