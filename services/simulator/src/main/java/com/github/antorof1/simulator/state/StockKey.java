package com.github.antorof1.simulator.state;

public record StockKey(String warehouseId, String sku) {
    @Override
    public String toString() {
        return sku + " @ " + warehouseId;
    }
}
