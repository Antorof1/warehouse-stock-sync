package com.github.antorof1.reorder;

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

    protected ReorderRule() {
    }

    public ReorderRule(ReorderRuleId id, Integer reorderPoint, Integer reorderUpTo, ReorderStatus status) {
        this.id = id;
        this.reorderPoint = reorderPoint;
        this.reorderUpTo = reorderUpTo;
        this.status = status;
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
}
