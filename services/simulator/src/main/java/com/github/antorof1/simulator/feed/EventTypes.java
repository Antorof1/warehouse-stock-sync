package com.github.antorof1.simulator.feed;

public final class EventTypes {
    public static final String HEADER = "__TypeId__";

    public static final String MOVEMENT_RECORDED = "warehouse.movement.recorded";
    public static final String STOCK_LEVEL_CHANGED = "inventory.stock-level.changed";
    public static final String PURCHASE_ORDER_PLACED = "reorder.purchase-order.placed";
    public static final String PURCHASE_ORDER_COMPLETED = "reorder.purchase-order.completed";
    public static final String PURCHASE_ORDER_EXPIRED = "reorder.purchase-order.expired";

    private EventTypes() {
    }
}
