package com.github.antorof1.reorder.rule;

import org.junit.jupiter.api.Test;

import static com.github.antorof1.reorder.utils.ReorderUtils.SKU;
import static com.github.antorof1.reorder.utils.ReorderUtils.rule;
import static org.assertj.core.api.Assertions.assertThat;

class ReorderRuleTest {
    private final ReorderRule rule = rule(SKU, 10, 50);

    @Test
    void ordersUpToTheTargetWhenStockIsBelowTheReorderPoint() {
        assertThat(rule.quantityToOrder(4)).isEqualTo(46);
    }

    @Test
    void ordersWhenStockIsExactlyAtTheReorderPoint() {
        assertThat(rule.quantityToOrder(10)).isEqualTo(40);
    }

    @Test
    void ordersNothingWhenStockIsAboveTheReorderPoint() {
        assertThat(rule.quantityToOrder(11)).isZero();
    }

    @Test
    void coversTheShortfallWhenStockIsNegative() {
        assertThat(rule.quantityToOrder(-5)).isEqualTo(55);
    }

    @Test
    void ordersNothingPositiveWhenTheTargetIsNotAboveTheStockLevel() {
        ReorderRule misconfigured = rule(SKU, 10, 8);

        assertThat(misconfigured.quantityToOrder(9))
            .as("the processor only places an order for a positive quantity")
            .isNotPositive();
    }
}
