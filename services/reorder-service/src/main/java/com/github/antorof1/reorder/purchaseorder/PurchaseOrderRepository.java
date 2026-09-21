package com.github.antorof1.reorder.purchaseorder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    Optional<PurchaseOrder> findBySkuAndWarehouseIdAndStatus(String sku, String warehouseId, PurchaseOrderStatus status);

    @Query(value = """
        UPDATE purchase_orders
        SET status = 'EXPIRED'
        WHERE status = 'OPEN'
          AND created_at < :cutoff
        RETURNING *
        """, nativeQuery = true)
    List<PurchaseOrder> expireOpenPurchaseOrdersCreatedBefore(@Param("cutoff") Instant cutoff);
}
