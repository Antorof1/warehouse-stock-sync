package com.github.antorof1.reorder.rule;

import com.github.antorof1.events.MovementReason;
import jakarta.persistence.*;

@Entity
@Table(name = "reorder_rules")
public class ReorderRule {
    @EmbeddedId
    private ReorderRuleId id;

    @Column(nullable = false)
    private Integer reorderPoint;

    @Column(nullable = false)
    private Integer reorderUpTo;

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    private ReorderStatus status;

    @Column(nullable = false)
    private Integer pendingQuantity;

    protected ReorderRule() {
    }

    public ReorderRule(ReorderRuleId id, Integer reorderPoint, Integer reorderUpTo, ReorderStatus status, Integer pendingQuantity) {
        this.id = id;
        this.reorderPoint = reorderPoint;
        this.reorderUpTo = reorderUpTo;
        this.status = status;
        this.pendingQuantity = pendingQuantity;
    }

    public ReorderRuleId getId() {
        return id;
    }

    public void setId(ReorderRuleId id) {
        this.id = id;
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

    public ReorderStatus getStatus() {
        return status;
    }

    public void setStatus(ReorderStatus status) {
        this.status = status;
    }

    public Integer getPendingQuantity() {
        return pendingQuantity;
    }

    public void setPendingQuantity(Integer pendingQuantity) {
        this.pendingQuantity = pendingQuantity;
    }
}
