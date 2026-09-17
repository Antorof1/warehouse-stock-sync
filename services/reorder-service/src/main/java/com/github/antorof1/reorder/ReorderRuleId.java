package com.github.antorof1.reorder;

import jakarta.persistence.Embeddable;

@Embeddable
public record ReorderRuleId(
    String sku,
    String warehouseId
) {
}
