package com.github.antorof1.reorder.rule;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "reorder_rules")
public class ReorderRule {
    @EmbeddedId
    private ReorderRuleId id;

    @Column(nullable = false)
    private Integer reorderPoint;

    @Column(nullable = false)
    private Integer reorderUpTo;

    protected ReorderRule() {
    }

    public ReorderRule(ReorderRuleId id, Integer reorderPoint, Integer reorderUpTo) {
        this.id = id;
        this.reorderPoint = reorderPoint;
        this.reorderUpTo = reorderUpTo;
    }

    public int quantityToOrder(int stockLevel) {
        return stockLevel <= reorderPoint ? reorderUpTo - stockLevel : 0;
    }

    public ReorderRuleId getId() {
        return id;
    }

    public Integer getReorderPoint() {
        return reorderPoint;
    }

    public void setReorderPoint(Integer reorderPoint) {
        this.reorderPoint = reorderPoint;
    }

    public Integer getReorderUpTo() {
        return reorderUpTo;
    }

    public void setReorderUpTo(Integer reorderUpTo) {
        this.reorderUpTo = reorderUpTo;
    }
}
