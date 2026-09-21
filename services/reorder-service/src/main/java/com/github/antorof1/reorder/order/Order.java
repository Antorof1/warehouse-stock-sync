package com.github.antorof1.reorder.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private String warehouseId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer receivedQuantity;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    protected Order() {
    }

    public Order(String sku, String warehouseId, int quantity) {
        this.sku = sku;
        this.warehouseId = warehouseId;
        this.quantity = quantity;
        this.receivedQuantity = 0;
        this.status = OrderStatus.OPEN;
    }

    public void receive(int quantityReceived) {
        int outstanding = quantity - receivedQuantity;

        receivedQuantity += Math.min(quantityReceived, outstanding);
        if (receivedQuantity >= quantity) {
            status = OrderStatus.COMPLETED;
        }
    }

    public boolean isOpen() {
        return status == OrderStatus.OPEN;
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Integer getReceivedQuantity() {
        return receivedQuantity;
    }

    public OrderStatus getStatus() {
        return status;
    }
}
