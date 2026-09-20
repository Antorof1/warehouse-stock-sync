package com.github.antorof1.inventory.stock;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.inventory.IntegrationTest;
import com.github.antorof1.inventory.utils.StockUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.antorof1.inventory.utils.StockUtils.OCCURRED_AT;
import static com.github.antorof1.inventory.utils.StockUtils.WAREHOUSE_ID;
import static com.github.antorof1.inventory.utils.StockUtils.newEventId;
import static com.github.antorof1.inventory.utils.StockUtils.stockId;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class StockConcurrencyTest {
    private static final int CONCURRENT_MOVEMENTS = 8;
    private static final int STARTING_QUANTITY = 100;

    @Autowired
    private StockService stockService;

    @Autowired
    private StockRepository stockRepository;

    @Test
    void keepsEveryConcurrentMovementForTheSameSku() throws Exception {
        String sku = StockUtils.uniqueSku();
        stockRepository.save(StockUtils.stock(sku, STARTING_QUANTITY));

        applyConcurrently(sku);

        assertThat(stockRepository.findById(stockId(sku)).orElseThrow().getQuantity())
            .as("read-modify-write without a version column loses concurrent updates")
            .isEqualTo(STARTING_QUANTITY - CONCURRENT_MOVEMENTS);
    }

    private void applyConcurrently(String sku) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_MOVEMENTS);
        CountDownLatch startLine = new CountDownLatch(1);

        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_MOVEMENTS; i++) {
                results.add(executor.submit(() -> {
                    startLine.await();
                    stockService.applyMovement(
                        sku, WAREHOUSE_ID, -1, MovementReason.PICK, OCCURRED_AT
                    );

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
