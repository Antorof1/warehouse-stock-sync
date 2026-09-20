package com.github.antorof1.inventory.stock;

import com.github.antorof1.inventory.IntegrationTest;
import com.github.antorof1.inventory.utils.StockUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static com.github.antorof1.inventory.utils.StockUtils.MAX_FIELD_LENGTH;
import static com.github.antorof1.inventory.utils.StockUtils.WAREHOUSE_ID;
import static com.github.antorof1.inventory.utils.StockUtils.stockId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Transactional
class StockRepositoryTest {
    @Autowired
    private StockRepository stockRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void reloadsARowByItsCompositeKey() {
        String sku = StockUtils.uniqueSku();
        stockRepository.saveAndFlush(StockUtils.stock(sku, 12));
        entityManager.clear();

        Stock reloaded = stockRepository.findById(stockId(sku)).orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(stockId(sku));
        assertThat(reloaded.getQuantity()).isEqualTo(12);
    }

    @Test
    void applyDeltaCreatesTheRowWhenTheSkuIsNewToTheWarehouse() {
        String sku = StockUtils.uniqueSku();

        Integer quantity = stockRepository.applyDelta(sku, WAREHOUSE_ID, 7);
        entityManager.clear();

        assertThat(quantity).isEqualTo(7);
        assertThat(quantityOf(sku)).isEqualTo(7);
    }

    @Test
    void applyDeltaCreatesTheRowEvenWhenTheFirstMovementIsAPick() {
        String sku = StockUtils.uniqueSku();

        Integer quantity = stockRepository.applyDelta(sku, WAREHOUSE_ID, -3);
        entityManager.clear();

        assertThat(quantity).isEqualTo(-3);
        assertThat(quantityOf(sku)).isEqualTo(-3);
    }

    @Test
    void applyDeltaAddsToAnExistingRowAndReturnsTheResult() {
        String sku = StockUtils.uniqueSku();
        stockRepository.saveAndFlush(StockUtils.stock(sku, 10));
        entityManager.clear();

        Integer quantity = stockRepository.applyDelta(sku, WAREHOUSE_ID, -4);
        entityManager.clear();

        assertThat(quantity).isEqualTo(6);
        assertThat(quantityOf(sku)).isEqualTo(6);
    }

    @Test
    void applyDeltaAccumulatesSuccessiveMovements() {
        String sku = StockUtils.uniqueSku();

        stockRepository.applyDelta(sku, WAREHOUSE_ID, 5);
        Integer quantity = stockRepository.applyDelta(sku, WAREHOUSE_ID, -2);
        entityManager.clear();

        assertThat(quantity).isEqualTo(3);
        assertThat(quantityOf(sku)).isEqualTo(3);
    }

    @Test
    void applyDeltaTouchesOnlyTheGivenWarehouse() {
        String sku = StockUtils.uniqueSku();
        stockRepository.saveAndFlush(new Stock(new StockId(sku, "WH-WROCLAW"), 10));
        stockRepository.saveAndFlush(new Stock(new StockId(sku, "WH-GDANSK"), 3));
        entityManager.clear();

        stockRepository.applyDelta(sku, "WH-WROCLAW", 5);
        entityManager.clear();

        assertThat(stockRepository.findById(new StockId(sku, "WH-WROCLAW")).orElseThrow().getQuantity()).isEqualTo(15);
        assertThat(stockRepository.findById(new StockId(sku, "WH-GDANSK")).orElseThrow().getQuantity()).isEqualTo(3);
    }

    @Test
    void mapsFieldsToSnakeCaseColumns() {
        String sku = StockUtils.uniqueSku();
        stockRepository.saveAndFlush(StockUtils.stock(sku, 12));

        Object[] row = (Object[]) entityManager
            .createNativeQuery("SELECT sku, warehouse_id, quantity FROM stock WHERE sku = :sku")
            .setParameter("sku", sku)
            .getSingleResult();

        assertThat(row[0]).isEqualTo(sku);
        assertThat(row[1]).isEqualTo(WAREHOUSE_ID);
        assertThat(row[2]).isEqualTo(12);
    }

    @Test
    void holdsTheSameSkuIndependentlyPerWarehouse() {
        String sku = StockUtils.uniqueSku();
        stockRepository.saveAndFlush(new Stock(new StockId(sku, "WH-WROCLAW"), 12));
        stockRepository.saveAndFlush(new Stock(new StockId(sku, "WH-GDANSK"), 3));
        entityManager.clear();

        assertThat(stockRepository.findById(new StockId(sku, "WH-WROCLAW")).orElseThrow().getQuantity()).isEqualTo(12);
        assertThat(stockRepository.findById(new StockId(sku, "WH-GDANSK")).orElseThrow().getQuantity()).isEqualTo(3);
    }

    @Test
    void updatesTheExistingRowWhenTheSameKeyIsSavedAgain() {
        String sku = StockUtils.uniqueSku();
        stockRepository.saveAndFlush(StockUtils.stock(sku, 12));
        entityManager.clear();

        stockRepository.saveAndFlush(StockUtils.stock(sku, 20));
        entityManager.clear();

        assertThat(stockRepository.findById(stockId(sku)).orElseThrow().getQuantity()).isEqualTo(20);
        assertThat(rowCountFor(sku)).isEqualTo(1);
    }

    @Test
    void storesNegativeQuantities() {
        String sku = StockUtils.uniqueSku();

        stockRepository.saveAndFlush(StockUtils.stock(sku, -3));
        entityManager.clear();

        assertThat(stockRepository.findById(stockId(sku)).orElseThrow().getQuantity()).isEqualTo(-3);
    }

    @Test
    void rejectsAMissingQuantity() {
        Stock stock = new Stock(stockId(StockUtils.uniqueSku()), null);

        assertThatThrownBy(() -> stockRepository.saveAndFlush(stock))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsASkuLongerThanTheColumn() {
        Stock stock = StockUtils.stock(StockUtils.ofLength('X', MAX_FIELD_LENGTH + 1), 1);

        assertThatThrownBy(() -> stockRepository.saveAndFlush(stock))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAWarehouseIdLongerThanTheColumn() {
        Stock stock = new Stock(
            new StockId(StockUtils.uniqueSku(), StockUtils.ofLength('X', MAX_FIELD_LENGTH + 1)), 1
        );

        assertThatThrownBy(() -> stockRepository.saveAndFlush(stock))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Integer quantityOf(String sku) {
        return stockRepository.findById(stockId(sku)).orElseThrow().getQuantity();
    }

    private long rowCountFor(String sku) {
        return ((Number) entityManager
            .createNativeQuery("SELECT COUNT(*) FROM stock WHERE sku = :sku")
            .setParameter("sku", sku)
            .getSingleResult()).longValue();
    }
}
