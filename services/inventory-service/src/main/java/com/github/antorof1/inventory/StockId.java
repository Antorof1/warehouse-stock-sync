package com.github.antorof1.inventory;

import jakarta.persistence.Embeddable;

@Embeddable
public record StockId(
    String sku,
    String warehouseId
) {
}
