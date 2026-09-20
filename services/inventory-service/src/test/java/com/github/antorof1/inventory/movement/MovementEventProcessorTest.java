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
import static org.mockito.Mockito.never;
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
        givenNotProcessed(event);

        processor.process(event);

        verify(stockService).applyMovement(
            SKU, WAREHOUSE_ID, -4, MovementReason.PICK, OCCURRED_AT
        );
    }

    @Test
    void marksTheEventProcessedOnlyAfterApplyingIt() {
        MovementEvent event = movementEvent(SKU, 7);
        givenNotProcessed(event);

        processor.process(event);

        InOrder inOrder = inOrder(stockService, processedEventService);
        inOrder.verify(stockService).applyMovement(anyString(), anyString(), anyInt(), any(), any());
        inOrder.verify(processedEventService).markProcessed(event.id());
    }

    @Test
    void skipsAnEventThatWasAlreadyProcessed() {
        MovementEvent event = movementEvent(SKU, 7);
        given(processedEventService.isProcessed(event.id())).willReturn(true);

        processor.process(event);

        verifyNoInteractions(stockService);
        verify(processedEventService, never()).markProcessed(any());
    }

    @Test
    void doesNotMarkTheEventProcessedWhenApplyingItFails() {
        MovementEvent event = movementEvent(SKU, 7);
        givenNotProcessed(event);
        willThrow(new DataIntegrityViolationException("stock write failed"))
            .given(stockService).applyMovement(anyString(), anyString(), anyInt(), any(), any());

        assertThatThrownBy(() -> processor.process(event))
            .as("the listener must see the failure so the transaction rolls back and Kafka redelivers")
            .isInstanceOf(DataIntegrityViolationException.class);

        verify(processedEventService, never()).markProcessed(any());
    }

    private void givenNotProcessed(MovementEvent event) {
        given(processedEventService.isProcessed(event.id())).willReturn(false);
    }
}
