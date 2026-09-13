package com.github.antorof1.warehouse.movement;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "movements")
public class Movement {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

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
