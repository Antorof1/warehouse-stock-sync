package com.github.antorof1.inventory.stock;

import jakarta.persistence.Embeddable;

@Embeddable
public record StockId(
    String sku,
    String warehouseId
) {
}
