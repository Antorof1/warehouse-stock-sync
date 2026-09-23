package com.github.antorof1.reorder.stock;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderCompletedEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderPlacedEvent;
import com.github.antorof1.reorder.IntegrationTest;
import com.github.antorof1.reorder.idempotency.ProcessedEventRepository;
import com.github.antorof1.reorder.purchaseorder.PurchaseOrder;
import com.github.antorof1.reorder.purchaseorder.PurchaseOrderRepository;
import com.github.antorof1.reorder.purchaseorder.PurchaseOrderStatus;
import com.github.antorof1.reorder.rule.ReorderRuleRepository;
import com.github.antorof1.reorder.utils.ReorderUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.Comparator;
import java.util.List;

import static com.github.antorof1.reorder.utils.ReorderUtils.WAREHOUSE_ID;
import static com.github.antorof1.reorder.utils.ReorderUtils.rule;
import static com.github.antorof1.reorder.utils.ReorderUtils.stockLevelEvent;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@RecordApplicationEvents
class StockLevelEventProcessorTest {
    @Autowired
    private StockLevelEventProcessor processor;

    @Autowired
    private ReorderRuleRepository reorderRuleRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Test
    void ignoresASkuWithoutARule() {
        String sku = ReorderUtils.uniqueSku();
        StockLevelEvent event = stockLevelEvent(sku, 12, 0, MovementReason.PICK);

        processor.process(event);

        assertThat(ordersFor(sku)).isEmpty();
        assertThat(placedEventsFor(sku)).isEmpty();
        assertThat(processedEventRepository.existsById(event.id()))
            .as("the event is claimed even when no rule applies")
            .isTrue();
    }

    @Test
    void placesNothingWhileStockIsAboveTheReorderPoint() {
        String sku = givenRule();

        processor.process(stockLevelEvent(sku, 20, 11, MovementReason.PICK));

        assertThat(ordersFor(sku)).isEmpty();
        assertThat(placedEventsFor(sku)).isEmpty();
    }

    @Test
    void placesAnOrderUpToTheTargetWhenStockReachesTheReorderPoint() {
        String sku = givenRule();

        processor.process(stockLevelEvent(sku, 12, 8, MovementReason.PICK));

        PurchaseOrder order = openOrderFor(sku);
        assertThat(order.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(order.getQuantity()).isEqualTo(42);
        assertThat(order.getReceivedQuantity()).isZero();

        List<PurchaseOrderPlacedEvent> placed = placedEventsFor(sku);
        assertThat(placed).hasSize(1);
        assertThat(placed.getFirst().id()).isNotNull();
        assertThat(placed.getFirst().warehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(placed.getFirst().quantity()).isEqualTo(42);
    }

    @Test
    void keepsOneOpenOrderWhileStockKeepsFalling() {
        String sku = givenRule();
        processor.process(stockLevelEvent(sku, 12, 8, MovementReason.PICK));

        processor.process(stockLevelEvent(sku, 8, 3, MovementReason.PICK));
        processor.process(stockLevelEvent(sku, 3, 1, MovementReason.DAMAGE));

        assertThat(ordersFor(sku)).hasSize(1);
        assertThat(openOrderFor(sku).getQuantity()).isEqualTo(42);
        assertThat(openOrderFor(sku).getReceivedQuantity())
            .as("only receipts count against an open order")
            .isZero();
        assertThat(placedEventsFor(sku)).hasSize(1);
    }

    @Test
    void countsAPartialReceiptAgainstTheOpenOrder() {
        String sku = givenRule();
        processor.process(stockLevelEvent(sku, 12, 8, MovementReason.PICK));

        processor.process(stockLevelEvent(sku, 8, 20, MovementReason.RECEIPT));

        PurchaseOrder order = openOrderFor(sku);
        assertThat(order.getReceivedQuantity()).isEqualTo(12);
        assertThat(completedEventsFor(sku)).isEmpty();
        assertThat(ordersFor(sku)).hasSize(1);
    }

    @Test
    void completesTheOrderWhenTheReceiptCoversIt() {
        String sku = givenRule();
        processor.process(stockLevelEvent(sku, 12, 8, MovementReason.PICK));
        PurchaseOrder placed = openOrderFor(sku);

        processor.process(stockLevelEvent(sku, 8, 50, MovementReason.RECEIPT));

        PurchaseOrder order = purchaseOrderRepository.findById(placed.getId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.COMPLETED);
        assertThat(order.getReceivedQuantity()).isEqualTo(42);

        List<PurchaseOrderCompletedEvent> completed = completedEventsFor(sku);
        assertThat(completed).hasSize(1);
        assertThat(completed.getFirst().warehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(completed.getFirst().orderedQuantity()).isEqualTo(42);
        assertThat(completed.getFirst().placedAt()).isEqualTo(placed.getCreatedAt());

        assertThat(ordersFor(sku))
            .as("stock is back above the reorder point, so no new order is placed")
            .hasSize(1);
        assertThat(placedEventsFor(sku)).hasSize(1);
    }

    @Test
    void placesANewOrderWhenTheCompletingReceiptLeavesStockLow() {
        String sku = givenRule();
        processor.process(stockLevelEvent(sku, 12, 8, MovementReason.PICK));
        reorderRuleRepository.save(rule(sku, 60, 100));

        processor.process(stockLevelEvent(sku, 8, 50, MovementReason.RECEIPT));

        List<PurchaseOrder> orders = ordersFor(sku);
        assertThat(orders).extracting(PurchaseOrder::getStatus)
            .containsExactly(PurchaseOrderStatus.COMPLETED, PurchaseOrderStatus.OPEN);
        assertThat(orders.getLast().getQuantity()).isEqualTo(50);
        assertThat(completedEventsFor(sku)).hasSize(1);
        assertThat(placedEventsFor(sku)).hasSize(2);
    }

    @Test
    void placesAnOrderOnAReceiptWhenNoOrderIsOpenAndStockIsStillLow() {
        String sku = givenRule();

        processor.process(stockLevelEvent(sku, -10, 5, MovementReason.RECEIPT));

        assertThat(openOrderFor(sku).getQuantity()).isEqualTo(45);
        assertThat(completedEventsFor(sku)).isEmpty();
    }

    @Test
    void processesARedeliveredEventOnlyOnce() {
        String sku = givenRule();
        StockLevelEvent event = stockLevelEvent(sku, 12, 8, MovementReason.PICK);
        processor.process(event);
        processor.process(stockLevelEvent(sku, 8, 20, MovementReason.RECEIPT));

        processor.process(event);

        assertThat(ordersFor(sku)).hasSize(1);
        assertThat(placedEventsFor(sku)).hasSize(1);
    }

    @Test
    void doesNotCountARedeliveredReceiptTwice() {
        String sku = givenRule();
        processor.process(stockLevelEvent(sku, 12, 8, MovementReason.PICK));
        StockLevelEvent receipt = stockLevelEvent(sku, 8, 20, MovementReason.RECEIPT);

        processor.process(receipt);
        processor.process(receipt);

        assertThat(openOrderFor(sku).getReceivedQuantity()).isEqualTo(12);
    }

    private String givenRule() {
        String sku = ReorderUtils.uniqueSku();
        reorderRuleRepository.save(rule(sku));

        return sku;
    }

    private PurchaseOrder openOrderFor(String sku) {
        return purchaseOrderRepository.findBySkuAndWarehouseIdAndStatus(sku, WAREHOUSE_ID, PurchaseOrderStatus.OPEN)
            .orElseThrow(() -> new AssertionError("No open purchase order for sku " + sku));
    }

    private List<PurchaseOrder> ordersFor(String sku) {
        return purchaseOrderRepository.findAll().stream()
            .filter(order -> order.getSku().equals(sku))
            .sorted(Comparator.comparing(PurchaseOrder::getCreatedAt))
            .toList();
    }

    private List<PurchaseOrderPlacedEvent> placedEventsFor(String sku) {
        return applicationEvents.stream(PurchaseOrderPlacedEvent.class)
            .filter(event -> event.sku().equals(sku))
            .toList();
    }

    private List<PurchaseOrderCompletedEvent> completedEventsFor(String sku) {
        return applicationEvents.stream(PurchaseOrderCompletedEvent.class)
            .filter(event -> event.sku().equals(sku))
            .toList();
    }
}
