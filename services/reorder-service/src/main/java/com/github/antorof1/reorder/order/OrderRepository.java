package com.github.antorof1.reorder.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findBySkuAndWarehouseIdAndStatus(String sku, String warehouseId, OrderStatus status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE orders
        SET status = 'EXPIRED'
        WHERE status = 'OPEN'
          AND created_at < :cutoff
        """, nativeQuery = true)
    int expireOpenOrdersCreatedBefore(@Param("cutoff") Instant cutoff);
}
