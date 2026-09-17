package com.github.antorof1.reorder.rule;

import com.github.antorof1.events.MovementReason;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ReorderRuleService {
    private final ReorderRuleRepository reorderRuleRepository;

    public ReorderRuleService(ReorderRuleRepository reorderRuleRepository) {
        this.reorderRuleRepository = reorderRuleRepository;
    }

    @Transactional
    public ReorderDecision applyStockLevel(ReorderRuleId id, Integer currentQuantity, Integer previousQuantity, MovementReason reason) {
        Optional<ReorderRule> ruleOptional = reorderRuleRepository.findById(id);
        if (ruleOptional.isEmpty()) return ReorderDecision.NO_RULE;

        ReorderRule rule = ruleOptional.get();
        return rule.apply(currentQuantity, previousQuantity, reason);
    }
}
