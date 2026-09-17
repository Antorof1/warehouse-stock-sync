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

    public ReorderDecision apply(Integer currentQuantity, Integer previousQuantity, MovementReason reason) {
        if (status == ReorderStatus.IN_PROGRESS && reason == MovementReason.RECEIPT) {
            pendingQuantity -= currentQuantity - previousQuantity;
            if (pendingQuantity > 0) {
                return ReorderDecision.NO_CHANGE;
            }

            if (currentQuantity <= reorderPoint) {
                pendingQuantity = reorderUpTo - currentQuantity;
                return new ReorderDecision.ResolvedAndTriggered(id, pendingQuantity);
            }

            status = ReorderStatus.IDLE;
            pendingQuantity = 0;
            return new ReorderDecision.Resolved(id);
        }

        if (status == ReorderStatus.IDLE && currentQuantity <= reorderPoint) {
            status = ReorderStatus.IN_PROGRESS;
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
