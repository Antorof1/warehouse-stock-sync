package com.github.antorof1.inventory.stock;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.Topics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static com.github.antorof1.inventory.utils.StockUtils.OCCURRED_AT;
import static com.github.antorof1.inventory.utils.StockUtils.SKU;
import static com.github.antorof1.inventory.utils.StockUtils.WAREHOUSE_ID;
import static com.github.antorof1.inventory.utils.StockUtils.newEventId;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockLevelEventPublisherTest {
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private StockLevelEventPublisher stockLevelEventPublisher;

    @Test
    void sendsEventToStockLevelTopicKeyedBySku() {
        given(kafkaTemplate.send(anyString(), anyString(), any())).willReturn(CompletableFuture.completedFuture(null));
        StockLevelEvent event = event();

        stockLevelEventPublisher.onStockLevelEvent(event);

        verify(kafkaTemplate).send(Topics.STOCK_LEVEL_EVENTS, event.sku(), event);
    }

    @Test
    void doesNotPropagateSendFailures() {
        given(kafkaTemplate.send(anyString(), anyString(), any()))
            .willReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        assertThatCode(() -> stockLevelEventPublisher.onStockLevelEvent(event()))
            .doesNotThrowAnyException();
    }

    private StockLevelEvent event() {
        return new StockLevelEvent(newEventId(), SKU, WAREHOUSE_ID, 6, 10, MovementReason.PICK, OCCURRED_AT);
    }
}
