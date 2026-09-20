package com.github.antorof1.inventory.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends JpaRepository<Stock, StockId> {
    @Query(value = """
        INSERT INTO stock (sku, warehouse_id, quantity)
        VALUES (:sku, :warehouseId, :quantityDelta)
        ON CONFLICT (sku, warehouse_id)
        DO UPDATE SET quantity = stock.quantity + EXCLUDED.quantity
        RETURNING quantity
        """, nativeQuery = true)
    Integer applyDelta(
        @Param("sku") String sku,
        @Param("warehouseId") String warehouseId,
        @Param("quantityDelta") Integer quantityDelta
    );
}
