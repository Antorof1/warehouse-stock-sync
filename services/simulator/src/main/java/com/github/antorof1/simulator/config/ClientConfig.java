package com.github.antorof1.simulator.config;

import com.github.antorof1.simulator.client.ReorderClient;
import com.github.antorof1.simulator.client.WarehouseClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class ClientConfig {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    }

    @Bean
    public WarehouseClient warehouseClient(HttpClient httpClient, SimulatorProperties properties) {
        return new WarehouseClient(restClient(httpClient, properties.services().warehouse()));
    }

    @Bean
    public ReorderClient reorderClient(HttpClient httpClient, SimulatorProperties properties) {
        return new ReorderClient(restClient(httpClient, properties.services().reorder()));
    }

    public static RestClient restClient(HttpClient httpClient, URI baseUrl) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build();
    }
}
