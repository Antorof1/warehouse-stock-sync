package com.github.antorof1.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock")
public class Stock {
    @EmbeddedId
    private StockId id;

    @Column(nullable = false)
    private Integer quantity;

    protected Stock() {
    }

    public Stock(StockId id, Integer quantity) {
        this.id = id;
        this.quantity = quantity;
    }

    public StockId getId() {
        return id;
    }

    public void setId(StockId id) {
        this.id = id;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
