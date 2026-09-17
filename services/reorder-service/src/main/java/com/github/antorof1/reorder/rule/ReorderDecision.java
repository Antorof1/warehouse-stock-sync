package com.github.antorof1.reorder.rule;

public sealed interface ReorderDecision {
    ReorderDecision NO_CHANGE = new NoChange();
    ReorderDecision NO_RULE = new NoRule();

    record Triggered(ReorderRuleId id, Integer quantityToOrder) implements ReorderDecision {}
    record Resolved(ReorderRuleId id) implements ReorderDecision {}
    record ResolvedAndTriggered(ReorderRuleId id, Integer quantityToOrder) implements ReorderDecision {}
    record NoChange() implements ReorderDecision {}
    record NoRule() implements ReorderDecision {}
}
