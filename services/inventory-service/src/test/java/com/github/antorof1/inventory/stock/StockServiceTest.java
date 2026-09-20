package com.github.antorof1.inventory.stock;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.StockLevelEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static com.github.antorof1.inventory.utils.StockUtils.OCCURRED_AT;
import static com.github.antorof1.inventory.utils.StockUtils.SKU;
import static com.github.antorof1.inventory.utils.StockUtils.WAREHOUSE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {
    private static final UUID MOVEMENT_ID = UUID.fromString("0199a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");

    @Mock
    private StockRepository stockRepository;

    @Mock
    private ApplicationEventPublisher publisher;

    @InjectMocks
    private StockService stockService;

    @Captor
    private ArgumentCaptor<StockLevelEvent> eventCaptor;

    @Test
    void handsTheDeltaToTheDatabaseInsteadOfComputingTheNewQuantity() {
        givenResultingQuantity(6);

        applyMovement(-4, MovementReason.PICK);

        verify(stockRepository).applyDelta(SKU, WAREHOUSE_ID, -4);
    }

    @Test
    void publishesAnEventDescribingTheTransition() {
        givenResultingQuantity(6);

        applyMovement(-4, MovementReason.PICK);

        StockLevelEvent event = capturedEvent();
        assertThat(event.sku()).isEqualTo(SKU);
        assertThat(event.warehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(event.previousLevel()).isEqualTo(10);
        assertThat(event.newLevel()).isEqualTo(6);
        assertThat(event.reason()).isEqualTo(MovementReason.PICK);
        assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void derivesThePreviousLevelFromTheQuantityTheDatabaseReturned() {
        givenResultingQuantity(7);

        applyMovement(7, MovementReason.RECEIPT);

        assertThat(capturedEvent().previousLevel())
            .as("a first movement for the sku leaves exactly its own delta behind")
            .isZero();
    }

    @Test
    void reportsAnOversellAsANegativeLevel() {
        givenResultingQuantity(-3);

        applyMovement(-5, MovementReason.PICK);

        StockLevelEvent event = capturedEvent();
        assertThat(event.newLevel())
            .as("downstream negative stock alerting depends on the oversell being reported")
            .isEqualTo(-3);
        assertThat(event.previousLevel()).isEqualTo(2);
    }

    @Test
    void givesTheEventItsOwnTimeOrderedIdRatherThanTheMovementId() {
        givenResultingQuantity(7);

        applyMovement(7, MovementReason.RECEIPT);

        UUID eventId = capturedEvent().id();
        assertThat(eventId).isNotNull().isNotEqualTo(MOVEMENT_ID);
        assertThat(eventId.version()).isEqualTo(7);
    }

    @Test
    void writesBeforeItPublishes() {
        givenResultingQuantity(7);

        applyMovement(7, MovementReason.RECEIPT);

        InOrder inOrder = inOrder(stockRepository, publisher);
        inOrder.verify(stockRepository).applyDelta(anyString(), anyString(), anyInt());
        inOrder.verify(publisher).publishEvent(any(StockLevelEvent.class));
    }

    @Test
    void publishesNothingWhenPersistingFails() {
        given(stockRepository.applyDelta(anyString(), anyString(), anyInt()))
            .willThrow(new DataIntegrityViolationException("sku too long"));

        assertThatThrownBy(() -> applyMovement(7, MovementReason.RECEIPT))
            .isInstanceOf(DataIntegrityViolationException.class);

        verifyNoInteractions(publisher);
    }

    private void applyMovement(int quantityDelta, MovementReason reason) {
        stockService.applyMovement(SKU, WAREHOUSE_ID, quantityDelta, reason, OCCURRED_AT);
    }

    private void givenResultingQuantity(int quantity) {
        given(stockRepository.applyDelta(anyString(), anyString(), anyInt())).willReturn(quantity);
    }

    private StockLevelEvent capturedEvent() {
        verify(publisher).publishEvent(eventCaptor.capture());

        return eventCaptor.getValue();
    }
}
