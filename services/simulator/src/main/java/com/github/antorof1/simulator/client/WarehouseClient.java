package com.github.antorof1.simulator.client;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class WarehouseClient {
    private final RestClient restClient;

    public WarehouseClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public void record(Movement movement) {
        restClient.post()
            .uri("/api/v1/movements")
            .contentType(MediaType.APPLICATION_JSON)
            .body(movement)
            .retrieve()
            .toBodilessEntity();
    }
}
