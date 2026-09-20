package com.github.antorof1.warehouse.movement;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.warehouse.IntegrationTest;
import com.github.antorof1.warehouse.utils.MovementUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.github.antorof1.warehouse.utils.MovementUtils.MAX_FIELD_LENGTH;
import static com.github.antorof1.warehouse.utils.MovementUtils.SKU;
import static com.github.antorof1.warehouse.utils.MovementUtils.WAREHOUSE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@IntegrationTest
@Transactional
class MovementRepositoryTest {
    @Autowired
    private MovementRepository movementRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void assignsVersion7UuidOnSave() {
        Movement saved = movementRepository.saveAndFlush(MovementUtils.movement());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId().version()).isEqualTo(7);
    }

    @Test
    void generatesTimeOrderedIds() {
        UUID first = movementRepository.saveAndFlush(MovementUtils.movement()).getId();
        UUID second = movementRepository.saveAndFlush(MovementUtils.movement()).getId();

        assertThat(first).isLessThan(second);
    }

    @Test
    void mapsFieldsToSnakeCaseColumns() {
        Movement saved = movementRepository.saveAndFlush(MovementUtils.movement());

        Object[] row = (Object[]) entityManager
            .createNativeQuery("SELECT sku, warehouse_id, quantity_delta, reason, occurred_at FROM movements WHERE id = :id")
            .setParameter("id", saved.getId())
            .getSingleResult();

        assertThat(row[0]).isEqualTo(SKU);
        assertThat(row[1]).isEqualTo(WAREHOUSE_ID);
        assertThat(row[2]).isEqualTo(MovementUtils.QUANTITY_DELTA);
        assertThat(row[3]).isEqualTo(MovementUtils.REASON.name());
    }

    @ParameterizedTest
    @EnumSource(MovementReason.class)
    void storesEveryReasonAsItsName(MovementReason reason) {
        Movement saved = movementRepository.saveAndFlush(
            new Movement(SKU, WAREHOUSE_ID, 1, reason, MovementUtils.OCCURRED_AT)
        );

        String stored = (String) entityManager
            .createNativeQuery("SELECT reason FROM movements WHERE id = :id")
            .setParameter("id", saved.getId())
            .getSingleResult();
        assertThat(stored).isEqualTo(reason.name());

        entityManager.clear();
        assertThat(movementRepository.findById(saved.getId()).orElseThrow().getReason()).isEqualTo(reason);
    }

    @Test
    void keepsOccurredAtToMicrosecondPrecision() {
        Instant withNanoseconds = Instant.parse("2026-09-20T10:15:30.123456789Z");

        Movement saved = movementRepository.saveAndFlush(
            new Movement(SKU, WAREHOUSE_ID, 1, MovementReason.RECEIPT, withNanoseconds)
        );
        entityManager.clear();

        Instant reloaded = movementRepository.findById(saved.getId()).orElseThrow().getOccurredAt();
        assertThat(reloaded.getNano() % 1000).isZero();
        assertThat(Duration.between(withNanoseconds, reloaded).abs()).isLessThan(Duration.ofNanos(1000));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requiredFields")
    void rejectsMissingRequiredField(String field, Consumer<Movement> clearField) {
        Movement movement = MovementUtils.movement();
        clearField.accept(movement);

        assertThatThrownBy(() -> movementRepository.saveAndFlush(movement))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsValuesLongerThanTheColumn() {
        Movement movement = MovementUtils.movement(MovementUtils.ofLength('X', MAX_FIELD_LENGTH + 1));

        assertThatThrownBy(() -> movementRepository.saveAndFlush(movement))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    static Stream<Arguments> requiredFields() {
        return Stream.of(
            arguments("sku", (Consumer<Movement>) movement -> movement.setSku(null)),
            arguments("warehouseId", (Consumer<Movement>) movement -> movement.setWarehouseId(null)),
            arguments("quantityDelta", (Consumer<Movement>) movement -> movement.setQuantityDelta(null)),
            arguments("reason", (Consumer<Movement>) movement -> movement.setReason(null)),
            arguments("occurredAt", (Consumer<Movement>) movement -> movement.setOccurredAt(null))
        );
    }
}
