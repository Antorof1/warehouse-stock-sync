package com.github.antorof1.simulator.scenario;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.Topics;
import com.github.antorof1.simulator.catalog.Catalog;
import com.github.antorof1.simulator.client.Movement;
import com.github.antorof1.simulator.config.SimulatorProperties;
import com.github.antorof1.simulator.state.PurchaseOrderView;
import com.github.antorof1.simulator.state.StockKey;
import com.github.antorof1.simulator.utils.FakeSystem;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.github.antorof1.simulator.utils.SimulatorUtils.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ScenariosTest {
    private final SimulatorProperties properties = properties();
    private final FakeSystem system = new FakeSystem(properties);

    @Test
    void reorderLifecycleCompletesAPurchaseOrder() {
        ScenarioRun.Status status = run(new ReorderLifecycleScenario(system.reorderClient()), Map.of());

        assertThat(status.state()).isEqualTo(ScenarioRun.State.SUCCEEDED);

        StockKey key = keyOf(system.movements().getFirst());
        assertThat(key.sku()).startsWith("DEMO-REORDER-");
        verify(system.reorderClient()).upsertRule(key, 10, 100);
        assertThat(system.movements()).extracting(Movement::quantityDelta, Movement::reason).containsExactly(
            tuple(50, MovementReason.RECEIPT),
            tuple(-45, MovementReason.PICK),
            tuple(40, MovementReason.RECEIPT),
            tuple(55, MovementReason.RECEIPT)
        );
        assertThat(system.state().order(key)).hasValueSatisfying(order -> {
            assertThat(order.status()).isEqualTo(PurchaseOrderView.Status.COMPLETED);
            assertThat(order.quantity()).isEqualTo(95);
        });
        assertThat(system.level(key)).isEqualTo(100);
    }

    @Test
    void notificationRulesTripEachRuleAndEndAtZero() {
        ScenarioRun.Status status = run(new NotificationRulesScenario(), Map.of());

        assertThat(status.state()).isEqualTo(ScenarioRun.State.SUCCEEDED);

        List<Movement> movements = system.movements();
        StockKey key = keyOf(movements.getFirst());
        assertThat(movements).extracting(Movement::reason).containsExactly(
            MovementReason.RECEIPT,
            MovementReason.DAMAGE,
            MovementReason.RECEIPT,
            MovementReason.PICK,
            MovementReason.ADJUSTMENT
        );
        assertThat(movements.get(2).quantityDelta()).isGreaterThanOrEqualTo(500);
        assertThat(movements.get(3).quantityDelta()).isLessThan(-movements.get(2).quantityDelta());
        assertThat(system.level(key)).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void poisonPillIsPublishedRawAndFollowedByAValidMovement() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        given(kafkaTemplate.send(sent.capture())).willAnswer(invocation -> {
            ProducerRecord<String, String> record = invocation.getArgument(0);
            PoisonPillScenario.MOVEMENT_CONSUMER_GROUPS.forEach(group ->
                system.state().onDeadLetter(Topics.MOVEMENT_EVENTS + "." + group + ".dlt", record.key()));
            return CompletableFuture.completedFuture(mock(SendResult.class));
        });

        ScenarioRun.Status status = run(new PoisonPillScenario(kafkaTemplate), Map.of());

        assertThat(status.state()).isEqualTo(ScenarioRun.State.SUCCEEDED);
        verify(kafkaTemplate).send(any(ProducerRecord.class));

        ProducerRecord<String, String> poison = sent.getValue();
        assertThat(poison.topic()).isEqualTo(Topics.MOVEMENT_EVENTS);
        assertThat(poison.key()).startsWith("DEMO-POISON-");
        assertThat(new String(poison.headers().lastHeader("__TypeId__").value(), StandardCharsets.UTF_8))
            .isEqualTo("warehouse.movement.recorded");
        assertThat(system.movements()).singleElement().satisfies(movement -> {
            assertThat(movement.sku()).isEqualTo(poison.key());
            assertThat(movement.reason()).isEqualTo(MovementReason.RECEIPT);
        });
    }

    @Test
    void poisonPillFailsWhenNothingIsDeadLettered() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        given(kafkaTemplate.send(any(ProducerRecord.class)))
            .willReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        SimulatorProperties quick = withAwaitTimeout(Duration.ofMillis(300));

        ScenarioRun.Status status = run(new PoisonPillScenario(kafkaTemplate), Map.of(), quick);

        assertThat(status.state()).isEqualTo(ScenarioRun.State.FAILED);
        assertThat(status.log().getLast().text()).contains("Timed out", ".inventory-service.dlt");
    }

    @Test
    void burstLeavesEveryItemWhereItStarted() {
        Catalog catalog = new Catalog(properties);
        ScenarioRun.Status status = run(new BurstScenario(catalog, system.feed()), Map.of(BurstScenario.SIZE_OPTION, 200));

        assertThat(status.state()).isEqualTo(ScenarioRun.State.SUCCEEDED);
        assertThat(system.movements()).hasSize(200).allSatisfy(movement ->
            assertThat(movement.reason()).isEqualTo(MovementReason.ADJUSTMENT));
        catalog.items().forEach(item -> assertThat(system.level(item.key())).isZero());
        assertThat(status.log()).anySatisfy(line -> assertThat(line.text()).contains("All 200 movements"));
    }

    private ScenarioRun.Status run(Scenario scenario, Map<String, Integer> options) {
        return run(scenario, options, properties);
    }

    private ScenarioRun.Status run(Scenario scenario, Map<String, Integer> options, SimulatorProperties properties) {
        ScenarioRunner runner = new ScenarioRunner(List.of(scenario), system.warehouseClient(), system.state(),
            Clock.systemUTC(), properties);

        runner.start(scenario.id(), options);
        await().atMost(Duration.ofSeconds(20))
            .until(() -> runner.status().map(status -> status.state() != ScenarioRun.State.RUNNING).orElse(false));

        return runner.status().orElseThrow();
    }

    private SimulatorProperties withAwaitTimeout(Duration timeout) {
        SimulatorProperties.Scenarios scenarios = properties.scenarios();

        return new SimulatorProperties(properties.services(), properties.links(), properties.catalog(),
            properties.traffic(), properties.supplier(),
            new SimulatorProperties.Scenarios(scenarios.warehouseId(), scenarios.skuPrefix(), scenarios.stepDelay(),
                timeout, scenarios.bulkQuantity(), scenarios.burstSize(), scenarios.burstConcurrency()),
            properties.feed());
    }

    private static StockKey keyOf(Movement movement) {
        return new StockKey(movement.warehouseId(), movement.sku());
    }
}
