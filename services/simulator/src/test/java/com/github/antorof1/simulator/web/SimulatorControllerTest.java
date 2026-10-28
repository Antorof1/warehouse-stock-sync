package com.github.antorof1.simulator.web;

import com.github.antorof1.simulator.config.SimulatorProperties;
import com.github.antorof1.simulator.scenario.BurstScenario;
import com.github.antorof1.simulator.scenario.Scenario;
import com.github.antorof1.simulator.exception.ScenarioBusyException;
import com.github.antorof1.simulator.scenario.ScenarioRun;
import com.github.antorof1.simulator.scenario.ScenarioRunner;
import com.github.antorof1.simulator.exception.UnknownScenarioException;
import com.github.antorof1.simulator.supplier.SupplierSimulator;
import com.github.antorof1.simulator.traffic.TrafficGenerator;
import com.github.antorof1.simulator.utils.SimulatorUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(SimulatorController.class)
class SimulatorControllerTest {
    private static final String API = "/api/v1/simulator";
    private static final TrafficGenerator.TrafficStatus RUNNING =
        new TrafficGenerator.TrafficStatus(true, 5.0, true, 12, 0, null, null);
    private static final ScenarioRun.Status STARTED = new ScenarioRun.Status(
        "burst", "Burst load", ScenarioRun.State.RUNNING, Instant.parse("2026-10-20T10:15:30Z"), null, List.of()
    );

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TrafficGenerator traffic;

    @MockitoBean
    private SupplierSimulator supplier;

    @MockitoBean
    private ScenarioRunner scenarios;

    @MockitoBean
    private SnapshotAssembler assembler;

    @MockitoBean
    private LiveUpdates liveUpdates;

    @MockitoBean
    private SimulatorProperties properties;

    @Test
    void servesThePanelConfig() {
        SimulatorProperties real = SimulatorUtils.properties();
        given(properties.links()).willReturn(real.links());
        given(properties.scenarios()).willReturn(real.scenarios());
        Scenario burst = mock(Scenario.class);
        given(burst.id()).willReturn("burst");
        given(burst.name()).willReturn("Burst load");
        given(burst.description()).willReturn("Lots of movements");
        given(scenarios.scenarios()).willReturn(List.of(burst));

        MvcTestResult result = mvc.get().uri(API + "/config").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.links[0].name").isEqualTo("Grafana");
        assertThat(result).bodyJson().extractingPath("$.scenarios[0].id").isEqualTo("burst");
        assertThat(result).bodyJson().extractingPath("$.defaultBurstSize").isEqualTo(40);
        assertThat(result).bodyJson().extractingPath("$.maxBurstSize").isEqualTo(BurstScenario.MAX_SIZE);
    }

    @Test
    void startsTraffic() {
        given(traffic.status()).willReturn(RUNNING);

        MvcTestResult result = mvc.post().uri(API + "/traffic/start").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.running").isEqualTo(true);
        verify(traffic).start();
    }

    @Test
    void stopsTraffic() {
        given(traffic.status()).willReturn(RUNNING);

        assertThat(mvc.post().uri(API + "/traffic/stop").exchange()).hasStatus(HttpStatus.OK);
        verify(traffic).stop();
    }

    @Test
    void changesTheTrafficRate() {
        given(traffic.status()).willReturn(RUNNING);

        MvcTestResult result = put("/traffic", "{\"rate\": 5}");

        assertThat(result).hasStatus(HttpStatus.OK);
        verify(traffic).setRate(5.0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"rate\": 0}", "{\"rate\": 51}"})
    void rejectsAnInvalidRate(String body) {
        MvcTestResult result = put("/traffic", body);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(traffic);
    }

    @Test
    void configuresTheSupplier() {
        given(supplier.status()).willReturn(new SupplierSimulator.SupplierStatus(false, 15, true, 0, 0, 0));

        MvcTestResult result = put("/supplier", "{\"enabled\": false, \"leadTimeSeconds\": 15}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.leadTimeSeconds").isEqualTo(15);
        verify(supplier).configure(false, Duration.ofSeconds(15));
    }

    @Test
    void rejectsAnInvalidLeadTime() {
        assertThat(put("/supplier", "{\"enabled\": true, \"leadTimeSeconds\": 0}")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(supplier);
    }

    @Test
    void startsAScenarioWithItsSize() {
        given(scenarios.start("burst", Map.of(BurstScenario.SIZE_OPTION, 500))).willReturn(STARTED);

        MvcTestResult result = mvc.post().uri(API + "/scenarios/burst")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"size\": 500}")
            .exchange();

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(result).bodyJson().extractingPath("$.state").isEqualTo("RUNNING");
    }

    @Test
    void startsAScenarioWithoutABody() {
        given(scenarios.start("burst", Map.of())).willReturn(STARTED);

        assertThat(mvc.post().uri(API + "/scenarios/burst").exchange()).hasStatus(HttpStatus.ACCEPTED);
    }

    @Test
    void rejectsAnOversizedBurst() {
        MvcTestResult result = mvc.post().uri(API + "/scenarios/burst")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"size\": " + (BurstScenario.MAX_SIZE + 1) + "}")
            .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(scenarios);
    }

    @Test
    void reportsAnUnknownScenarioAsNotFound() {
        given(scenarios.start(eq("missing"), anyMap())).willThrow(new UnknownScenarioException("missing"));

        MvcTestResult result = mvc.post().uri(API + "/scenarios/missing").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void reportsABusyRunnerAsConflict() {
        given(scenarios.start(eq("burst"), anyMap())).willThrow(new ScenarioBusyException("Reorder lifecycle"));

        MvcTestResult result = mvc.post().uri(API + "/scenarios/burst").exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Scenario Reorder lifecycle is still running");
    }

    @Test
    void stopsTheRunningScenario() {
        given(scenarios.stop()).willReturn(true);

        assertThat(mvc.delete().uri(API + "/scenarios/current").exchange()).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void reportsNothingToStop() {
        given(scenarios.stop()).willReturn(false);

        assertThat(mvc.delete().uri(API + "/scenarios/current").exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    private MvcTestResult put(String path, String body) {
        return mvc.put().uri(API + path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .exchange();
    }
}
