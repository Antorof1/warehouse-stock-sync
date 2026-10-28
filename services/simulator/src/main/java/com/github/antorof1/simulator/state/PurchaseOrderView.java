package com.github.antorof1.simulator.state;

import java.time.Instant;

public record PurchaseOrderView(
    Status status,
    int quantity,
    int received,
    Instant placedAt,
    Instant closedAt
) {
    public enum Status {
        OPEN,
        COMPLETED,
        EXPIRED
    }

    static PurchaseOrderView placed(int quantity, Instant placedAt) {
        return new PurchaseOrderView(Status.OPEN, quantity, 0, placedAt, null);
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }

    PurchaseOrderView receive(int units) {
        return new PurchaseOrderView(status, quantity, Math.min(quantity, received + units), placedAt, closedAt);
    }

    PurchaseOrderView close(Status status, int received, Instant closedAt) {
        return new PurchaseOrderView(status, quantity, received, placedAt, closedAt);
    }
}
