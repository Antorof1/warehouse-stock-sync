package com.github.antorof1.reorder.purchaseorder;

import com.github.antorof1.events.purchaseorder.PurchaseOrderExpiredEvent;
import com.github.antorof1.outbox.OutboxEventRepository;
import com.github.antorof1.reorder.IntegrationTest;
import com.github.antorof1.reorder.utils.RecordedOutboxEvents;
import com.github.antorof1.reorder.utils.ReorderUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.github.antorof1.reorder.utils.ReorderUtils.PAST_TTL;
import static com.github.antorof1.reorder.utils.ReorderUtils.WAREHOUSE_ID;
import static com.github.antorof1.reorder.utils.ReorderUtils.ageOrder;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class PurchaseOrderServiceTest {
    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void expiresAnOpenOrderOlderThanTheTtl() {
        String sku = ReorderUtils.uniqueSku();
        PurchaseOrder order = openOrder(sku, PAST_TTL);

        purchaseOrderService.cleanUpExpiredOrders();

        assertThat(statusOf(order)).isEqualTo(PurchaseOrderStatus.EXPIRED);
    }

    @Test
    void recordsAnExpiredEventWithWhatWasReceived() {
        String sku = ReorderUtils.uniqueSku();
        PurchaseOrder order = new PurchaseOrder(sku, WAREHOUSE_ID, 40);
        order.receive(15);
        order = purchaseOrderRepository.save(order);
        ageOrder(jdbcTemplate, order.getId(), PAST_TTL);
        Instant before = Instant.now();

        purchaseOrderService.cleanUpExpiredOrders();

        List<PurchaseOrderExpiredEvent> events = expiredEventsFor(sku);
        assertThat(events).hasSize(1);
        PurchaseOrderExpiredEvent event = events.getFirst();
        assertThat(event.id()).isNotNull();
        assertThat(event.warehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(event.orderedQuantity()).isEqualTo(40);
        assertThat(event.receivedQuantity()).isEqualTo(15);
        assertThat(event.occurredAt()).isAfterOrEqualTo(before);
    }

    @Test
    void leavesAnOpenOrderYoungerThanTheTtl() {
        String sku = ReorderUtils.uniqueSku();
        PurchaseOrder order = openOrder(sku, Duration.ofDays(6));

        purchaseOrderService.cleanUpExpiredOrders();

        assertThat(statusOf(order)).isEqualTo(PurchaseOrderStatus.OPEN);
        assertThat(expiredEventsFor(sku)).isEmpty();
    }

    @Test
    void leavesAnOldCompletedOrder() {
        String sku = ReorderUtils.uniqueSku();
        PurchaseOrder order = new PurchaseOrder(sku, WAREHOUSE_ID, 40);
        order.receive(40);
        order = purchaseOrderRepository.save(order);
        ageOrder(jdbcTemplate, order.getId(), PAST_TTL);

        purchaseOrderService.cleanUpExpiredOrders();

        assertThat(statusOf(order)).isEqualTo(PurchaseOrderStatus.COMPLETED);
        assertThat(expiredEventsFor(sku)).isEmpty();
    }

    @Test
    void expiresAnOrderOnlyOnce() {
        String sku = ReorderUtils.uniqueSku();
        openOrder(sku, PAST_TTL);

        purchaseOrderService.cleanUpExpiredOrders();
        purchaseOrderService.cleanUpExpiredOrders();

        assertThat(expiredEventsFor(sku)).hasSize(1);
    }

    private PurchaseOrder openOrder(String sku, Duration age) {
        PurchaseOrder order = purchaseOrderRepository.save(new PurchaseOrder(sku, WAREHOUSE_ID, 40));
        ageOrder(jdbcTemplate, order.getId(), age);

        return order;
    }

    private PurchaseOrderStatus statusOf(PurchaseOrder order) {
        return purchaseOrderRepository.findById(order.getId()).orElseThrow().getStatus();
    }

    private List<PurchaseOrderExpiredEvent> expiredEventsFor(String sku) {
        return new RecordedOutboxEvents(outboxEventRepository, objectMapper)
            .purchaseOrderEventsFor(sku, PurchaseOrderExpiredEvent.class);
    }
}
