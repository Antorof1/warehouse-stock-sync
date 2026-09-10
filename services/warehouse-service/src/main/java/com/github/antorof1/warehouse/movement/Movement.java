package com.github.antorof1.warehouse.movement;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
public class Movement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private String warehouseId;

    @Column(nullable = false)
    private Integer quantityDelta;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MovementReason reason;

    @Column(nullable = false)
    private Instant occurredAt;

    protected Movement() {}
}
