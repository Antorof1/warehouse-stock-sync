package com.github.antorof1.reorder.stock;

import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.PurchaseOrderEvent;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.reorder.idempotency.ProcessedEventService;
import com.github.antorof1.reorder.order.Order;
import com.github.antorof1.reorder.order.OrderRepository;
import com.github.antorof1.reorder.order.OrderStatus;
import com.github.antorof1.reorder.rule.ReorderRule;
import com.github.antorof1.reorder.rule.ReorderRuleId;
import com.github.antorof1.reorder.rule.ReorderRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class StockLevelEventProcessor {
    private static final Logger log = LoggerFactory.getLogger(StockLevelEventProcessor.class);

    private final ReorderRuleRepository reorderRuleRepository;
    private final OrderRepository orderRepository;
    private final ProcessedEventService processedEventService;
    private final ApplicationEventPublisher publisher;

    public StockLevelEventProcessor(ReorderRuleRepository reorderRuleRepository,
                                    OrderRepository orderRepository,
                                    ProcessedEventService processedEventService,
                                    ApplicationEventPublisher publisher) {
        this.reorderRuleRepository = reorderRuleRepository;
        this.orderRepository = orderRepository;
        this.processedEventService = processedEventService;
        this.publisher = publisher;
    }

    @Transactional
    public void process(StockLevelEvent event) {
        if (processedEventService.isProcessed(event.id())) return;

        ReorderRuleId ruleId = new ReorderRuleId(event.sku(), event.warehouseId());
        reorderRuleRepository.findAndLockById(ruleId).ifPresentOrElse(
            rule -> reorder(rule, event),
            () -> log.debug("No reorder rule for {}", ruleId)
        );

        processedEventService.markProcessed(event.id());
    }

    private void reorder(ReorderRule rule, StockLevelEvent event) {
        Optional<Order> openOrder = orderRepository
            .findBySkuAndWarehouseIdAndStatus(event.sku(), event.warehouseId(), OrderStatus.OPEN);

        if (openOrder.isPresent()) {
            if (event.reason() != MovementReason.RECEIPT) return;

            Order order = openOrder.get();
            order.receive(event.newLevel() - event.previousLevel());
            if (order.isOpen()) return;
        }

        int quantity = rule.quantityToOrder(event.newLevel());
        if (quantity > 0) {
            Order saved = orderRepository.save(new Order(event.sku(), event.warehouseId(), quantity));

            PurchaseOrderEvent orderEvent = new PurchaseOrderEvent(
                saved.getId(),
                saved.getSku(),
                saved.getWarehouseId(),
                saved.getQuantity(),
                Instant.now()
            );

            publisher.publishEvent(orderEvent);
        }
    }
}
