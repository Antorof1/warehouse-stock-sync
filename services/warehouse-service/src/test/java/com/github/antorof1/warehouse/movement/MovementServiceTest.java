package com.github.antorof1.warehouse.movement;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.Topics;
import com.github.antorof1.outbox.OutboxEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static com.github.antorof1.warehouse.utils.MovementUtils.SKU;
import static com.github.antorof1.warehouse.utils.MovementUtils.WAREHOUSE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MovementServiceTest {
    private static final UUID GENERATED_ID = UUID.fromString("0199a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");

    @Mock
    private MovementRepository movementRepository;

    @Mock
    private OutboxEventService outboxEventService;

    @InjectMocks
    private MovementService movementService;

    @Captor
    private ArgumentCaptor<Movement> movementCaptor;

    @Captor
    private ArgumentCaptor<MovementEvent> eventCaptor;

    @Test
    void persistsRequestedFields() {
        givenRepositoryAssignsId();

        movementService.createMovement(SKU, WAREHOUSE_ID, 7, MovementReason.RECEIPT);

        Movement persisted = capturedMovement();
        assertThat(persisted.getSku()).isEqualTo(SKU);
        assertThat(persisted.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(persisted.getQuantityDelta()).isEqualTo(7);
        assertThat(persisted.getReason()).isEqualTo(MovementReason.RECEIPT);
        assertThat(persisted.getId()).as("id is assigned by the database").isNull();
    }

    @Test
    void stampsOccurredAtItself() {
        givenRepositoryAssignsId();

        Instant before = Instant.now();
        movementService.createMovement(SKU, WAREHOUSE_ID, 7, MovementReason.RECEIPT);
        Instant after = Instant.now();

        assertThat(capturedMovement().getOccurredAt()).isBetween(before, after);
    }

    @Test
    void returnsThePersistedMovement() {
        givenRepositoryAssignsId();

        Movement result = movementService.createMovement(SKU, WAREHOUSE_ID, 7, MovementReason.RECEIPT);

        assertThat(result.getId()).isEqualTo(GENERATED_ID);
    }

    @Test
    void recordsEventCarryingTheDatabaseGeneratedId() {
        givenRepositoryAssignsId();

        movementService.createMovement(SKU, WAREHOUSE_ID, 7, MovementReason.RECEIPT);

        assertThat(capturedEvent().id()).isEqualTo(GENERATED_ID);
    }

    @Test
    void recordsEventMirroringThePersistedMovement() {
        givenRepositoryAssignsId();

        movementService.createMovement(SKU, WAREHOUSE_ID, -3, MovementReason.PICK);

        Movement persisted = capturedMovement();
        assertThat(capturedEvent()).isEqualTo(new MovementEvent(
            GENERATED_ID,
            persisted.getSku(),
            persisted.getWarehouseId(),
            persisted.getQuantityDelta(),
            persisted.getReason(),
            persisted.getOccurredAt()
        ));
    }

    @Test
    void recordsNothingWhenPersistingFails() {
        given(movementRepository.save(any(Movement.class))).willThrow(new DataIntegrityViolationException("sku too long"));

        assertThatThrownBy(() -> movementService.createMovement(SKU, WAREHOUSE_ID, 7, MovementReason.RECEIPT))
            .isInstanceOf(DataIntegrityViolationException.class);

        verifyNoInteractions(outboxEventService);
    }

    @Test
    void recordsEventOnTheMovementTopicKeyedBySkuUnderTheMovementId() {
        givenRepositoryAssignsId();

        movementService.createMovement(SKU, WAREHOUSE_ID, 7, MovementReason.RECEIPT);

        verify(outboxEventService).record(eq(GENERATED_ID), eq(Topics.MOVEMENT_EVENTS), eq(SKU), any(MovementEvent.class));
    }

    @Test
    void propagatesOutboxFailureSoTheTransactionRollsBack() {
        givenRepositoryAssignsId();
        willThrow(new IllegalArgumentException("no type mapping"))
            .given(outboxEventService).record(any(), any(), any(), any());

        assertThatThrownBy(() -> movementService.createMovement(SKU, WAREHOUSE_ID, 7, MovementReason.RECEIPT))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroQuantityDelta() {
        assertThatThrownBy(() -> movementService.createMovement(SKU, WAREHOUSE_ID, 0, MovementReason.ADJUSTMENT))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(movementRepository, outboxEventService);
    }

    private void givenRepositoryAssignsId() {
        given(movementRepository.save(any(Movement.class))).willAnswer(invocation -> {
            Movement incoming = invocation.getArgument(0);

            Movement persisted = new Movement(
                incoming.getSku(),
                incoming.getWarehouseId(),
                incoming.getQuantityDelta(),
                incoming.getReason(),
                incoming.getOccurredAt()
            );
            persisted.setId(GENERATED_ID);

            return persisted;
        });
    }

    private Movement capturedMovement() {
        verify(movementRepository).save(movementCaptor.capture());

        return movementCaptor.getValue();
    }

    private MovementEvent capturedEvent() {
        verify(outboxEventService).record(any(), any(), any(), eventCaptor.capture());

        return eventCaptor.getValue();
    }
}
