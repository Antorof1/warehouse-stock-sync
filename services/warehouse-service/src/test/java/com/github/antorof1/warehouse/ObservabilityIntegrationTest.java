package com.github.antorof1.warehouse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@AutoConfigureMetrics
class ObservabilityIntegrationTest {
    @Autowired
    private MockMvcTester mvc;

    @Test
    void reportsLivenessAndReadiness() {
        assertThat(mvc.get().uri("/actuator/health/liveness")).hasStatus(HttpStatus.OK);
        assertThat(mvc.get().uri("/actuator/health/readiness")).hasStatus(HttpStatus.OK);
    }

    @Test
    void exposesPrometheusMetricsTaggedWithTheServiceName() {
        assertThat(mvc.get().uri("/actuator/prometheus"))
            .hasStatus(HttpStatus.OK)
            .bodyText()
            .contains("jvm_memory_used_bytes{")
            .contains("application=\"warehouse-service\"")
            .contains("outbox_events_pending{")
            .contains("outbox_events_oldest_pending_age_seconds{");
    }
}
