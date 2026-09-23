package com.github.antorof1.reorder.stock;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.reorder.IntegrationTest;
import com.github.antorof1.reorder.purchaseorder.PurchaseOrderRepository;
import com.github.antorof1.reorder.rule.ReorderRuleRepository;
import com.github.antorof1.reorder.utils.ReorderUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.antorof1.reorder.utils.ReorderUtils.rule;
import static com.github.antorof1.reorder.utils.ReorderUtils.stockLevelEvent;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ReorderConcurrencyTest {
    private static final int CONCURRENT_EVENTS = 8;

    @Autowired
    private StockLevelEventProcessor processor;

    @Autowired
    private ReorderRuleRepository reorderRuleRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Test
    void placesOneOrderWhenLowStockEventsForTheSameSkuArriveTogether() throws Exception {
        String sku = ReorderUtils.uniqueSku();
        reorderRuleRepository.save(rule(sku));

        processConcurrently(sku);

        assertThat(purchaseOrderRepository.findAll())
            .filteredOn(order -> order.getSku().equals(sku))
            .as("the rule row lock serialises the check for an open order and the insert")
            .hasSize(1);
    }

    private void processConcurrently(String sku) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_EVENTS);
        CountDownLatch startLine = new CountDownLatch(1);

        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_EVENTS; i++) {
                int level = i;
                results.add(executor.submit(() -> {
                    startLine.await();
                    processor.process(stockLevelEvent(sku, level + 1, level, MovementReason.PICK));

                    return null;
                }));
            }

            startLine.countDown();
            for (Future<?> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
