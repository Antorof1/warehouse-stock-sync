package com.github.antorof1.simulator.scenario;

import com.github.antorof1.simulator.client.WarehouseClient;
import com.github.antorof1.simulator.exception.ScenarioBusyException;
import com.github.antorof1.simulator.exception.ScenarioFailedException;
import com.github.antorof1.simulator.exception.UnknownScenarioException;
import com.github.antorof1.simulator.state.SimulationState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static com.github.antorof1.simulator.utils.SimulatorUtils.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

class ScenarioRunnerTest {
    private final CountDownLatch release = new CountDownLatch(1);
    private final Scenario blocking = scenario("blocking", context -> {
        context.step("Waiting");
        release.await();
    });
    private final Scenario failing = scenario("failing", context -> {
        context.step("About to fail");
        throw new ScenarioFailedException("Boom");
    });
    private final ScenarioRunner runner = new ScenarioRunner(List.of(blocking, failing), mock(WarehouseClient.class),
        new SimulationState(), Clock.systemUTC(), properties());

    @Test
    void listsScenariosInOrder() {
        assertThat(runner.scenarios()).extracting(Scenario::id).containsExactly("blocking", "failing");
    }

    @Test
    void reportsNoStatusBeforeTheFirstRun() {
        assertThat(runner.status()).isEmpty();
    }

    @Test
    void runsAScenarioToCompletion() {
        ScenarioRun.Status started = runner.start("blocking", Map.of());
        assertThat(started.state()).isEqualTo(ScenarioRun.State.RUNNING);

        release.countDown();

        ScenarioRun.Status finished = awaitFinished();
        assertThat(finished.state()).isEqualTo(ScenarioRun.State.SUCCEEDED);
        assertThat(finished.finishedAt()).isNotNull();
        assertThat(finished.log()).extracting(ScenarioRun.LogLine::kind)
            .containsExactly(ScenarioRun.LogLine.Kind.STEP, ScenarioRun.LogLine.Kind.SUCCESS);
    }

    @Test
    void rejectsASecondScenarioWhileOneIsRunning() {
        runner.start("blocking", Map.of());

        assertThatThrownBy(() -> runner.start("failing", Map.of())).isInstanceOf(ScenarioBusyException.class);

        release.countDown();
    }

    @Test
    void rejectsAnUnknownScenario() {
        assertThatThrownBy(() -> runner.start("missing", Map.of())).isInstanceOf(UnknownScenarioException.class);
    }

    @Test
    void recordsAFailure() {
        runner.start("failing", Map.of());

        ScenarioRun.Status finished = awaitFinished();
        assertThat(finished.state()).isEqualTo(ScenarioRun.State.FAILED);
        assertThat(finished.log().getLast().text()).isEqualTo("Boom");
    }

    @Test
    void stopsARunningScenario() {
        runner.start("blocking", Map.of());

        assertThat(runner.stop()).isTrue();

        ScenarioRun.Status finished = awaitFinished();
        assertThat(finished.state()).isEqualTo(ScenarioRun.State.CANCELLED);
        assertThat(runner.stop()).isFalse();
    }

    @Test
    void allowsANewRunAfterTheLastOneFinished() {
        runner.start("failing", Map.of());
        awaitFinished();

        release.countDown();
        assertThat(runner.start("blocking", Map.of()).id()).isEqualTo("blocking");
    }

    private ScenarioRun.Status awaitFinished() {
        await().atMost(Duration.ofSeconds(5))
            .until(() -> runner.status().map(status -> status.state() != ScenarioRun.State.RUNNING).orElse(false));

        return runner.status().orElseThrow();
    }

    private static Scenario scenario(String id, ScenarioBody body) {
        return new Scenario() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String name() {
                return id;
            }

            @Override
            public String description() {
                return id + " scenario";
            }

            @Override
            public void run(ScenarioContext context) throws InterruptedException {
                body.run(context);
            }
        };
    }

    @FunctionalInterface
    private interface ScenarioBody {
        void run(ScenarioContext context) throws InterruptedException;
    }
}
