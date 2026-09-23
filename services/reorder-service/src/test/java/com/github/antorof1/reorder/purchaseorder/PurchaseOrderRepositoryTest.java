package com.github.antorof1.reorder.purchaseorder;

import com.github.antorof1.reorder.IntegrationTest;
import com.github.antorof1.reorder.utils.ReorderUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.antorof1.reorder.utils.ReorderUtils.PAST_TTL;
import static com.github.antorof1.reorder.utils.ReorderUtils.WAREHOUSE_ID;
import static com.github.antorof1.reorder.utils.ReorderUtils.ageOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Transactional
class PurchaseOrderRepositoryTest {
    private static final Duration TTL = Duration.ofDays(7);

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findsTheOpenOrderForASkuAndWarehouse() {
        String sku = ReorderUtils.uniqueSku();
        PurchaseOrder completed = purchaseOrderRepository.saveAndFlush(completedOrder(sku, 10));
        PurchaseOrder open = purchaseOrderRepository.saveAndFlush(new PurchaseOrder(sku, WAREHOUSE_ID, 20));

        assertThat(purchaseOrderRepository.findBySkuAndWarehouseIdAndStatus(sku, WAREHOUSE_ID, PurchaseOrderStatus.OPEN))
            .map(PurchaseOrder::getId)
            .contains(open.getId());
        assertThat(purchaseOrderRepository.findBySkuAndWarehouseIdAndStatus(sku, WAREHOUSE_ID, PurchaseOrderStatus.COMPLETED))
            .map(PurchaseOrder::getId)
            .contains(completed.getId());
    }

    @Test
    void setsTheCreationTimeOnInsert() {
        PurchaseOrder order = purchaseOrderRepository.saveAndFlush(
            new PurchaseOrder(ReorderUtils.uniqueSku(), WAREHOUSE_ID, 20)
        );

        assertThat(order.getCreatedAt())
            .as("the database default fills created_at and Hibernate reads it back")
            .isNotNull();
    }

    @Test
    void expiresOnlyOpenOrdersCreatedBeforeTheCutoff() {
        String staleSku = ReorderUtils.uniqueSku();
        String freshSku = ReorderUtils.uniqueSku();
        String completedSku = ReorderUtils.uniqueSku();
        PurchaseOrder stale = purchaseOrderRepository.saveAndFlush(new PurchaseOrder(staleSku, WAREHOUSE_ID, 20));
        PurchaseOrder fresh = purchaseOrderRepository.saveAndFlush(new PurchaseOrder(freshSku, WAREHOUSE_ID, 20));
        PurchaseOrder completed = purchaseOrderRepository.saveAndFlush(completedOrder(completedSku, 20));
        ageOrder(jdbcTemplate, stale.getId(), PAST_TTL);
        ageOrder(jdbcTemplate, completed.getId(), PAST_TTL);
        entityManager.clear();

        List<PurchaseOrder> expired = purchaseOrderRepository.expireOpenPurchaseOrdersCreatedBefore(
            Instant.now().minus(TTL)
        );

        assertThat(expired).extracting(PurchaseOrder::getId)
            .contains(stale.getId())
            .doesNotContain(fresh.getId(), completed.getId());
        assertThat(statusOf(stale.getId())).isEqualTo("EXPIRED");
        assertThat(statusOf(fresh.getId())).isEqualTo("OPEN");
        assertThat(statusOf(completed.getId())).isEqualTo("COMPLETED");
    }

    @Test
    void returnsTheExpiredOrdersWithTheirQuantities() {
        String sku = ReorderUtils.uniqueSku();
        PurchaseOrder order = new PurchaseOrder(sku, WAREHOUSE_ID, 20);
        order.receive(5);
        order = purchaseOrderRepository.saveAndFlush(order);
        ageOrder(jdbcTemplate, order.getId(), PAST_TTL);
        entityManager.clear();

        PurchaseOrder expired = purchaseOrderRepository.expireOpenPurchaseOrdersCreatedBefore(Instant.now().minus(TTL))
            .stream()
            .filter(candidate -> candidate.getSku().equals(sku))
            .findFirst()
            .orElseThrow();

        assertThat(expired.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(expired.getQuantity()).isEqualTo(20);
        assertThat(expired.getReceivedQuantity()).isEqualTo(5);
        assertThat(expired.getStatus())
            .as("RETURNING * reads the row after the update")
            .isEqualTo(PurchaseOrderStatus.EXPIRED);
    }

    @Test
    void refusesASecondOpenOrderForTheSameSkuAndWarehouse() {
        String sku = ReorderUtils.uniqueSku();
        purchaseOrderRepository.saveAndFlush(new PurchaseOrder(sku, WAREHOUSE_ID, 20));

        assertThatThrownBy(() -> purchaseOrderRepository.saveAndFlush(new PurchaseOrder(sku, WAREHOUSE_ID, 30)))
            .as("the partial unique index keeps one open order per sku and warehouse")
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsANewOpenOrderOnceThePreviousOneIsClosed() {
        String sku = ReorderUtils.uniqueSku();
        purchaseOrderRepository.saveAndFlush(completedOrder(sku, 20));

        purchaseOrderRepository.saveAndFlush(new PurchaseOrder(sku, WAREHOUSE_ID, 30));

        assertThat(purchaseOrderRepository.findBySkuAndWarehouseIdAndStatus(sku, WAREHOUSE_ID, PurchaseOrderStatus.OPEN))
            .isPresent();
    }

    @Test
    void acceptsOpenOrdersForTheSameSkuInDifferentWarehouses() {
        String sku = ReorderUtils.uniqueSku();
        purchaseOrderRepository.saveAndFlush(new PurchaseOrder(sku, WAREHOUSE_ID, 20));

        purchaseOrderRepository.saveAndFlush(new PurchaseOrder(sku, "WH-GDANSK", 20));

        assertThat(purchaseOrderRepository.findBySkuAndWarehouseIdAndStatus(sku, "WH-GDANSK", PurchaseOrderStatus.OPEN))
            .isPresent();
    }

    private PurchaseOrder completedOrder(String sku, int quantity) {
        PurchaseOrder order = new PurchaseOrder(sku, WAREHOUSE_ID, quantity);
        order.receive(quantity);

        return order;
    }

    private String statusOf(UUID id) {
        return jdbcTemplate.queryForObject("SELECT status FROM purchase_orders WHERE id = ?", String.class, id);
    }
}
