package com.github.antorof1.simulator.client;

import com.github.antorof1.simulator.state.StockKey;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

public class ReorderClient {
    private static final ParameterizedTypeReference<List<ReorderRule>> RULE_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public ReorderClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public void upsertRule(StockKey key, int reorderPoint, int reorderUpTo) {
        restClient.put()
            .uri("/api/v1/reorder-rules/{warehouseId}/{sku}", key.warehouseId(), key.sku())
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("reorderPoint", reorderPoint, "reorderUpTo", reorderUpTo))
            .retrieve()
            .toBodilessEntity();
    }

    public List<ReorderRule> listRules() {
        List<ReorderRule> rules = restClient.get()
            .uri("/api/v1/reorder-rules")
            .retrieve()
            .body(RULE_LIST);

        return rules == null ? List.of() : rules;
    }
}
