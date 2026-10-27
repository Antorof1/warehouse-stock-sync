package com.github.antorof1.reorder.rule.dto;

import com.github.antorof1.reorder.rule.ReorderRule;

public record ReorderRuleResponse(
    String sku,
    String warehouseId,
    Integer reorderPoint,
    Integer reorderUpTo
) {
    public static ReorderRuleResponse fromEntity(ReorderRule rule) {
        return new ReorderRuleResponse(
            rule.getId().sku(),
            rule.getId().warehouseId(),
            rule.getReorderPoint(),
            rule.getReorderUpTo()
        );
    }
}
