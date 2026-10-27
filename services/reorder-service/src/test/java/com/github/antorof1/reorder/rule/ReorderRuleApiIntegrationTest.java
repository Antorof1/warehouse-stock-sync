package com.github.antorof1.reorder.rule;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.reorder.IntegrationTest;
import com.github.antorof1.reorder.purchaseorder.PurchaseOrder;
import com.github.antorof1.reorder.purchaseorder.PurchaseOrderRepository;
import com.github.antorof1.reorder.purchaseorder.PurchaseOrderStatus;
import com.github.antorof1.reorder.stock.StockLevelEventProcessor;
import com.github.antorof1.reorder.utils.ReorderUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.github.antorof1.reorder.utils.ReorderUtils.WAREHOUSE_ID;
import static com.github.antorof1.reorder.utils.ReorderUtils.rule;
import static com.github.antorof1.reorder.utils.ReorderUtils.ruleId;
import static com.github.antorof1.reorder.utils.ReorderUtils.stockLevelEvent;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ReorderRuleApiIntegrationTest {
    private static final String RULE_URI = "/api/v1/reorder-rules/{warehouseId}/{sku}";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ReorderRuleRepository reorderRuleRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private StockLevelEventProcessor processor;

    @Test
    void createsAndThenUpdatesARule() {
        String sku = ReorderUtils.uniqueSku();

        assertThat(put(sku, 10, 50)).hasStatus(HttpStatus.CREATED);
        assertThat(put(sku, 5, 20)).hasStatus(HttpStatus.OK);

        ReorderRule persisted = reorderRuleRepository.findById(ruleId(sku)).orElseThrow();
        assertThat(persisted.getReorderPoint()).isEqualTo(5);
        assertThat(persisted.getReorderUpTo()).isEqualTo(20);
    }

    @Test
    void readsBackAStoredRule() {
        String sku = ReorderUtils.uniqueSku();
        reorderRuleRepository.save(rule(sku, 7, 30));

        MvcTestResult single = mvc.get().uri(RULE_URI, WAREHOUSE_ID, sku).exchange();
        MvcTestResult all = mvc.get().uri("/api/v1/reorder-rules").exchange();

        assertThat(single).hasStatus(HttpStatus.OK);
        assertThat(single).bodyJson().extractingPath("$.reorderPoint").isEqualTo(7);
        assertThat(single).bodyJson().extractingPath("$.reorderUpTo").isEqualTo(30);
        assertThat(all).hasStatus(HttpStatus.OK);
        assertThat(all).bodyJson().extractingPath("$[*].sku").asArray().contains(sku);
    }

    @Test
    void deletesAStoredRule() {
        String sku = ReorderUtils.uniqueSku();
        reorderRuleRepository.save(rule(sku));

        assertThat(mvc.delete().uri(RULE_URI, WAREHOUSE_ID, sku).exchange()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(reorderRuleRepository.findById(ruleId(sku))).isEmpty();
        assertThat(mvc.delete().uri(RULE_URI, WAREHOUSE_ID, sku).exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectedRequestLeavesTheStoredRuleUntouched() {
        String sku = ReorderUtils.uniqueSku();
        reorderRuleRepository.save(rule(sku, 10, 50));

        assertThat(put(sku, 50, 10)).hasStatus(HttpStatus.BAD_REQUEST);

        ReorderRule persisted = reorderRuleRepository.findById(ruleId(sku)).orElseThrow();
        assertThat(persisted.getReorderPoint()).isEqualTo(10);
        assertThat(persisted.getReorderUpTo()).isEqualTo(50);
    }

    @Test
    void stockLevelProcessingUsesTheRuleConfiguredThroughTheApi() {
        String sku = ReorderUtils.uniqueSku();
        assertThat(put(sku, 20, 100)).hasStatus(HttpStatus.CREATED);

        processor.process(stockLevelEvent(sku, 25, 15, MovementReason.PICK));

        PurchaseOrder order = purchaseOrderRepository
            .findBySkuAndWarehouseIdAndStatus(sku, WAREHOUSE_ID, PurchaseOrderStatus.OPEN)
            .orElseThrow();
        assertThat(order.getQuantity()).isEqualTo(85);
    }

    private MvcTestResult put(String sku, int reorderPoint, int reorderUpTo) {
        return mvc.put()
            .uri(RULE_URI, WAREHOUSE_ID, sku)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"reorderPoint": %d, "reorderUpTo": %d}""".formatted(reorderPoint, reorderUpTo))
            .exchange();
    }
}
