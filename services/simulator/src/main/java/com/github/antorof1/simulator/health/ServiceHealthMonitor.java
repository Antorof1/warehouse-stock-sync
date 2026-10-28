package com.github.antorof1.simulator.health;

import com.github.antorof1.simulator.config.ClientConfig;
import com.github.antorof1.simulator.config.SimulatorProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ServiceHealthMonitor {
    public static final String UNKNOWN = "UNKNOWN";
    public static final String UNREACHABLE = "UNREACHABLE";

    private final Map<String, RestClient> clients = new LinkedHashMap<>();
    private final Map<String, String> statuses = new ConcurrentHashMap<>();

    public ServiceHealthMonitor(HttpClient httpClient, SimulatorProperties properties) {
        SimulatorProperties.Services services = properties.services();
        Map<String, URI> urls = new LinkedHashMap<>();
        urls.put("warehouse-service", services.warehouse());
        urls.put("inventory-service", services.inventory());
        urls.put("reorder-service", services.reorder());
        urls.put("notification-service", services.notification());

        urls.forEach((name, url) -> {
            clients.put(name, ClientConfig.restClient(httpClient, url));
            statuses.put(name, UNKNOWN);
        });
    }

    @Scheduled(fixedDelay = 5000)
    public void check() {
        clients.forEach((name, client) -> statuses.put(name, check(client)));
    }

    public Map<String, String> statuses() {
        Map<String, String> ordered = new LinkedHashMap<>();
        clients.keySet().forEach(name -> ordered.put(name, statuses.get(name)));

        return ordered;
    }

    private static String check(RestClient client) {
        try {
            Health health = client.get().uri("/actuator/health").retrieve().body(Health.class);
            return health == null || health.status() == null ? UNKNOWN : health.status();
        } catch (HttpStatusCodeException e) {
            Health health = e.getResponseBodyAs(Health.class);
            return health == null || health.status() == null ? UNKNOWN : health.status();
        } catch (RestClientException e) {
            return UNREACHABLE;
        }
    }

    private record Health(String status) {
    }
}
