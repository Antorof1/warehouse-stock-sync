package com.github.antorof1.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties("app")
public record SimulatorProperties(
    Services services,
    @DefaultValue List<Link> links,
    Catalog catalog,
    Traffic traffic,
    Supplier supplier,
    Scenarios scenarios,
    Feed feed
) {
    public record Services(URI warehouse, URI inventory, URI reorder, URI notification) {
    }

    public record Link(String name, String url) {
    }

    public record Catalog(List<String> warehouses, List<CatalogSku> skus) {
    }

    public record CatalogSku(String sku, String name, int reorderPoint, int reorderUpTo) {
    }

    public record Traffic(
        @DefaultValue("2.0") double rate,
        @DefaultValue("12") int maxPick
    ) {
    }

    public record Supplier(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("PT8S") Duration leadTime,
        @DefaultValue("true") boolean partialShipments
    ) {
    }

    public record Scenarios(
        @DefaultValue("WH-1") String warehouseId,
        @DefaultValue("DEMO-") String skuPrefix,
        @DefaultValue("PT2S") Duration stepDelay,
        @DefaultValue("PT30S") Duration awaitTimeout,
        @DefaultValue("600") int bulkQuantity,
        @DefaultValue("2000") int burstSize,
        @DefaultValue("16") int burstConcurrency
    ) {
    }

    public record Feed(@DefaultValue("300") int size) {
    }
}
