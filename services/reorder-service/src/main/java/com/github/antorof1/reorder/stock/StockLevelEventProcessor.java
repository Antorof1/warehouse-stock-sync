package com.github.antorof1.reorder.stock;

import com.github.antorof1.events.StockLevelEvent;
import com.github.antorof1.reorder.rule.ReorderDecision;
import com.github.antorof1.reorder.rule.ReorderRuleId;
import com.github.antorof1.reorder.rule.ReorderRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockLevelEventProcessor {
    private static final Logger log = LoggerFactory.getLogger(StockLevelEventProcessor.class);

    private final ReorderRuleService reorderRuleService;

    public StockLevelEventProcessor(ReorderRuleService reorderRuleService) {
        this.reorderRuleService = reorderRuleService;
    }

    @Transactional
    public void process(StockLevelEvent event) {
        ReorderRuleId ruleId = new ReorderRuleId(event.sku(), event.warehouseId());
        ReorderDecision decision = reorderRuleService.applyStockLevel(ruleId, event.newLevel(), event.previousLevel(), event.reason());

        switch (decision) {
            case ReorderDecision.Triggered triggered -> {

            }
            case ReorderDecision.Resolved resolved -> {

            }
            case ReorderDecision.ResolvedAndTriggered resolvedAndTriggered -> {

            }
            case ReorderDecision.NoChange noChange -> {}
            case ReorderDecision.NoRule noRule -> log.debug("No reorder rule for {}", ruleId);
        }
    }
}
