package com.github.antorof1.reorder.rule;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface ReorderRuleRepository extends JpaRepository<ReorderRule, ReorderRuleId> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReorderRule> findAndLockById(ReorderRuleId id);
}
