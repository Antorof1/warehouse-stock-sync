package com.github.antorof1.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, StockId> {
}
