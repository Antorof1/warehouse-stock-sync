package com.github.antorof1.simulator.web;

import com.github.antorof1.simulator.scenario.ScenarioRun;
import com.github.antorof1.simulator.state.PurchaseOrderView;
import com.github.antorof1.simulator.supplier.SupplierSimulator.SupplierStatus;
import com.github.antorof1.simulator.traffic.TrafficGenerator.TrafficStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record Snapshot(
    Instant at,
    Map<String, String> services,
    TrafficStatus traffic,
    SupplierStatus supplier,
    ScenarioRun.Status scenario,
    List<StockRow> stock,
    Map<String, Long> topicCounts
) {
    public record StockRow(
        String warehouseId,
        String sku,
        String name,
        boolean scenarioSku,
        Integer level,
        Instant updatedAt,
        Integer reorderPoint,
        Integer reorderUpTo,
        PurchaseOrderView order
    ) {
    }
}
