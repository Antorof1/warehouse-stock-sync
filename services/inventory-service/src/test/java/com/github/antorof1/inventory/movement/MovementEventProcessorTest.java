package com.github.antorof1.inventory.movement;

import com.github.antorof1.events.MovementEvent;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.inventory.idempotency.ProcessedEventService;
import com.github.antorof1.inventory.stock.StockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static com.github.antorof1.inventory.utils.StockUtils.OCCURRED_AT;
import static com.github.antorof1.inventory.utils.StockUtils.SKU;
import static com.github.antorof1.inventory.utils.StockUtils.WAREHOUSE_ID;
import static com.github.antorof1.inventory.utils.StockUtils.movementEvent;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MovementEventProcessorTest {
    @Mock
    private StockService stockService;

    @Mock
    private ProcessedEventService processedEventService;

    @InjectMocks
    private MovementEventProcessor processor;

    @Test
    void appliesTheMovementToStock() {
        MovementEvent event = movementEvent(SKU, -4, MovementReason.PICK);
        givenClaimed(event);

        processor.process(event);

        verify(stockService).applyMovement(
            SKU, WAREHOUSE_ID, -4, MovementReason.PICK, OCCURRED_AT
        );
    }

    @Test
    void claimsTheEventBeforeApplyingIt() {
        MovementEvent event = movementEvent(SKU, 7);
        givenClaimed(event);

        processor.process(event);

        InOrder inOrder = inOrder(processedEventService, stockService);
        inOrder.verify(processedEventService).tryMarkProcessed(event.id());
        inOrder.verify(stockService).applyMovement(anyString(), anyString(), anyInt(), any(), any());
    }

    @Test
    void skipsAnEventThatWasAlreadyProcessed() {
        MovementEvent event = movementEvent(SKU, 7);
        given(processedEventService.tryMarkProcessed(event.id())).willReturn(false);

        processor.process(event);

        verifyNoInteractions(stockService);
    }

    @Test
    void propagatesAFailureFromApplyingTheMovement() {
        MovementEvent event = movementEvent(SKU, 7);
        givenClaimed(event);
        willThrow(new DataIntegrityViolationException("stock write failed"))
            .given(stockService).applyMovement(anyString(), anyString(), anyInt(), any(), any());

        assertThatThrownBy(() -> processor.process(event))
            .as("the listener must see the failure so the transaction rolls the claim back and Kafka redelivers")
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void givenClaimed(MovementEvent event) {
        given(processedEventService.tryMarkProcessed(event.id())).willReturn(true);
    }
}
