package com.github.antorof1.reorder.stock;

import com.fasterxml.uuid.Generators;
import com.github.antorof1.events.MovementReason;
import com.github.antorof1.events.purchaseorder.PurchaseOrderCompletedEvent;
import com.github.antorof1.events.purchaseorder.PurchaseOrderPlacedEvent;
import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.reorder.idempotency.ProcessedEventService;
import com.github.antorof1.reorder.purchaseorder.PurchaseOrder;
import com.github.antorof1.reorder.purchaseorder.PurchaseOrderRepository;
import com.github.antorof1.reorder.purchaseorder.PurchaseOrderStatus;
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
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProcessedEventService processedEventService;
    private final ApplicationEventPublisher publisher;

    public StockLevelEventProcessor(ReorderRuleRepository reorderRuleRepository,
                                    PurchaseOrderRepository purchaseOrderRepository,
                                    ProcessedEventService processedEventService,
                                    ApplicationEventPublisher publisher) {
        this.reorderRuleRepository = reorderRuleRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.processedEventService = processedEventService;
        this.publisher = publisher;
    }

    @Transactional
    public void process(StockLevelEvent event) {
        if (!processedEventService.tryMarkProcessed(event.id())) return;

        ReorderRuleId ruleId = new ReorderRuleId(event.sku(), event.warehouseId());
        reorderRuleRepository.findAndLockById(ruleId).ifPresentOrElse(
            rule -> reorder(rule, event),
            () -> log.debug("No reorder rule for {}", ruleId)
        );
    }

    private void reorder(ReorderRule rule, StockLevelEvent event) {
        Optional<PurchaseOrder> openOrder = purchaseOrderRepository
            .findBySkuAndWarehouseIdAndStatus(event.sku(), event.warehouseId(), PurchaseOrderStatus.OPEN);

        if (openOrder.isPresent()) {
            if (event.reason() != MovementReason.RECEIPT) return;

            PurchaseOrder order = openOrder.get();
            order.receive(event.newLevel() - event.previousLevel());
            if (order.isOpen()) return;

            PurchaseOrderCompletedEvent completedEvent = new PurchaseOrderCompletedEvent(
                Generators.timeBasedEpochGenerator().generate(),
                order.getSku(),
                order.getWarehouseId(),
                order.getQuantity(),
                order.getCreatedAt(),
                Instant.now()
            );

            publisher.publishEvent(completedEvent);
        }

        int quantity = rule.quantityToOrder(event.newLevel());
        if (quantity > 0) {
            PurchaseOrder saved = purchaseOrderRepository.save(new PurchaseOrder(event.sku(), event.warehouseId(), quantity));

            PurchaseOrderPlacedEvent placedEvent = new PurchaseOrderPlacedEvent(
                Generators.timeBasedEpochGenerator().generate(),
                saved.getSku(),
                saved.getWarehouseId(),
                saved.getQuantity(),
                Instant.now()
            );

            publisher.publishEvent(placedEvent);
        }
    }
}
