package com.github.antorof1.simulator.scenario;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.Topics;
import com.github.antorof1.simulator.catalog.Catalog;
import com.github.antorof1.simulator.catalog.Catalog.CatalogItem;
import com.github.antorof1.simulator.exception.ScenarioFailedException;
import com.github.antorof1.simulator.feed.EventFeed;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(4)
public class BurstScenario implements Scenario {
    public static final String SIZE_OPTION = "size";
    public static final int MAX_SIZE = 20_000;

    static final int MAX_ADJUSTMENT = 5;
    static final Duration MIN_DRAIN_TIMEOUT = Duration.ofMinutes(2);

    private final Catalog catalog;
    private final EventFeed feed;

    public BurstScenario(Catalog catalog, EventFeed feed) {
        this.catalog = catalog;
        this.feed = feed;
    }

    @Override
    public String id() {
        return "burst";
    }

    @Override
    public String name() {
        return "Burst load";
    }

    @Override
    public String description() {
        return "Fires thousands of movements at once. Watch outbox backlog, consumer lag and latency in Grafana.";
    }

    @Override
    public void run(ScenarioContext context) throws InterruptedException {
        int pairs = Math.max(1, Math.min(MAX_SIZE, context.option(SIZE_OPTION, context.settings().burstSize())) / 2);
        int concurrency = context.settings().burstConcurrency();
        List<CatalogItem> items = catalog.items();
        if (items.isEmpty()) {
            throw new ScenarioFailedException("The catalog is empty");
        }

        long stockEventsBefore = feed.count(Topics.STOCK_LEVEL_EVENTS);
        AtomicInteger sent = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        context.step("Send %d movements to warehouse-service from %d concurrent clients"
            .formatted(pairs * 2, concurrency));
        Instant start = context.clock().instant();
        sendPairs(context, items, pairs, concurrency, sent, failed);
        Duration sendTime = Duration.between(start, context.clock().instant());

        context.info("Sent %d movements in %.1fs (%.0f/s), %d failed".formatted(
            sent.get(), seconds(sendTime), sent.get() / Math.max(0.001, seconds(sendTime)), failed.get()));
        if (sent.get() == 0) {
            throw new ScenarioFailedException("warehouse-service accepted none of the movements");
        }

        context.step("Wait for inventory-service to work through the backlog");
        long expected = stockEventsBefore + sent.get();
        Duration timeout = max(MIN_DRAIN_TIMEOUT, context.settings().awaitTimeout());
        context.await("%d stock level events".formatted(sent.get()),
            () -> feed.count(Topics.STOCK_LEVEL_EVENTS) >= expected, timeout);

        Duration total = Duration.between(start, context.clock().instant());
        context.info("All %d movements reached inventory.stock-level-events %.1fs after the burst started"
            .formatted(sent.get(), seconds(total)));
    }

    private void sendPairs(ScenarioContext context, List<CatalogItem> items, int pairs, int concurrency,
                           AtomicInteger sent, AtomicInteger failed) throws InterruptedException {
        Semaphore permits = new Semaphore(concurrency);
        int progressStep = Math.max(1, pairs / 4);
        List<Future<?>> futures = new ArrayList<>(pairs);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < pairs; i++) {
                permits.acquire();
                CatalogItem item = items.get(ThreadLocalRandom.current().nextInt(items.size()));
                int quantity = 1 + ThreadLocalRandom.current().nextInt(MAX_ADJUSTMENT);

                futures.add(executor.submit(() -> {
                    try {
                        sendPair(context, item, quantity, sent, failed);
                    } finally {
                        permits.release();
                    }
                }));

                if ((i + 1) % progressStep == 0 && i + 1 < pairs) {
                    context.info("Queued %d of %d movements".formatted((i + 1) * 2, pairs * 2));
                }
            }

            for (Future<?> future : futures) {
                future.get();
            }
        } catch (ExecutionException e) {
            throw new ScenarioFailedException("Burst failed: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            futures.forEach(future -> future.cancel(true));
            throw e;
        }
    }

    private static void sendPair(ScenarioContext context, CatalogItem item, int quantity, AtomicInteger sent,
                                 AtomicInteger failed) {
        try {
            context.record(item.key(), quantity, MovementReason.ADJUSTMENT);
            sent.incrementAndGet();
        } catch (RestClientException e) {
            failed.addAndGet(2);
            return;
        }

        try {
            context.record(item.key(), -quantity, MovementReason.ADJUSTMENT);
            sent.incrementAndGet();
        } catch (RestClientException e) {
            failed.incrementAndGet();
        }
    }

    private static double seconds(Duration duration) {
        return duration.toMillis() / 1000.0;
    }

    private static Duration max(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}
