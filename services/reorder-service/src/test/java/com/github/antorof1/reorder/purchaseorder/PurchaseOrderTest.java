package com.github.antorof1.reorder.purchaseorder;

import org.junit.jupiter.api.Test;

import static com.github.antorof1.reorder.utils.ReorderUtils.SKU;
import static com.github.antorof1.reorder.utils.ReorderUtils.WAREHOUSE_ID;
import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderTest {
    @Test
    void startsOpenWithNothingReceived() {
        PurchaseOrder order = order(40);

        assertThat(order.isOpen()).isTrue();
        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.OPEN);
        assertThat(order.getReceivedQuantity()).isZero();
    }

    @Test
    void staysOpenAfterAPartialReceipt() {
        PurchaseOrder order = order(40);

        order.receive(15);

        assertThat(order.isOpen()).isTrue();
        assertThat(order.getReceivedQuantity()).isEqualTo(15);
    }

    @Test
    void completesWhenTheFullQuantityArrives() {
        PurchaseOrder order = order(40);

        order.receive(40);

        assertThat(order.isOpen()).isFalse();
        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.COMPLETED);
        assertThat(order.getReceivedQuantity()).isEqualTo(40);
    }

    @Test
    void capsAnOverReceiptAtTheOrderedQuantity() {
        PurchaseOrder order = order(40);

        order.receive(55);

        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.COMPLETED);
        assertThat(order.getReceivedQuantity()).isEqualTo(40);
    }

    @Test
    void completesAcrossSeveralPartialReceipts() {
        PurchaseOrder order = order(40);

        order.receive(15);
        order.receive(20);
        assertThat(order.isOpen()).isTrue();
        order.receive(10);

        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.COMPLETED);
        assertThat(order.getReceivedQuantity()).isEqualTo(40);
    }

    private PurchaseOrder order(int quantity) {
        return new PurchaseOrder(SKU, WAREHOUSE_ID, quantity);
    }
}
