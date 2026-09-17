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
    private Integer pendingQuantity;

    protected ReorderRule() {
    }

    public ReorderRule(ReorderRuleId id, Integer reorderPoint, Integer reorderUpTo, Integer pendingQuantity) {
        this.id = id;
        this.reorderPoint = reorderPoint;
        this.reorderUpTo = reorderUpTo;
        this.pendingQuantity = pendingQuantity;
    }

    public ReorderDecision apply(Integer currentQuantity, Integer previousQuantity, MovementReason reason) {
        if (pendingQuantity > 0 && reason == MovementReason.RECEIPT) {
            pendingQuantity -= currentQuantity - previousQuantity;
            if (pendingQuantity > 0) {
                return ReorderDecision.NO_CHANGE;
            }

            if (currentQuantity <= reorderPoint) {
                pendingQuantity = reorderUpTo - currentQuantity;
                return new ReorderDecision.ResolvedAndTriggered(id, pendingQuantity);
            }

            pendingQuantity = 0;
            return new ReorderDecision.Resolved(id);
        }

        if (pendingQuantity == 0 && currentQuantity <= reorderPoint) {
            pendingQuantity = reorderUpTo - currentQuantity;
            return new ReorderDecision.Triggered(id, pendingQuantity);
        }

        return ReorderDecision.NO_CHANGE;
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

    public Integer getPendingQuantity() {
        return pendingQuantity;
    }

    public void setPendingQuantity(Integer pendingQuantity) {
        this.pendingQuantity = pendingQuantity;
    }
}
