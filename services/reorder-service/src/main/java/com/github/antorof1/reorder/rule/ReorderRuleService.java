package com.github.antorof1.reorder.rule;

import com.github.antorof1.reorder.exception.ReorderRuleNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ReorderRuleService {
    private static final Sort BY_WAREHOUSE_AND_SKU = Sort.by("id.warehouseId", "id.sku");

    private final ReorderRuleRepository reorderRuleRepository;

    public ReorderRuleService(ReorderRuleRepository reorderRuleRepository) {
        this.reorderRuleRepository = reorderRuleRepository;
    }

    public List<ReorderRule> findAll() {
        return reorderRuleRepository.findAll(BY_WAREHOUSE_AND_SKU);
    }

    public Optional<ReorderRule> find(ReorderRuleId id) {
        return reorderRuleRepository.findById(id);
    }

    @Transactional
    public boolean save(ReorderRuleId id, int reorderPoint, int reorderUpTo) {
        if (reorderUpTo <= reorderPoint) {
            throw new IllegalArgumentException("Reorder up to must be greater than reorder point");
        }

        Optional<ReorderRule> existing = reorderRuleRepository.findAndLockById(id);
        if (existing.isPresent()) {
            ReorderRule rule = existing.get();
            rule.setReorderPoint(reorderPoint);
            rule.setReorderUpTo(reorderUpTo);

            return false;
        }

        reorderRuleRepository.save(new ReorderRule(id, reorderPoint, reorderUpTo));

        return true;
    }

    @Transactional
    public void delete(ReorderRuleId id) {
        ReorderRule rule = reorderRuleRepository.findAndLockById(id)
            .orElseThrow(() -> new ReorderRuleNotFoundException(id));

        reorderRuleRepository.delete(rule);
    }
}
