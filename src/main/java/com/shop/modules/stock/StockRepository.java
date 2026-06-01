package com.shop.modules.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockRepository
        extends JpaRepository<Stock, UUID> {

    Optional<Stock> findByProductId(UUID productId);

    // Low stock items using new field name
    @Query("SELECT s FROM Stock s " +
            "JOIN FETCH s.product p " +
            "WHERE s.totalSecondaryUnits " +
            "< p.lowStockAlert " +
            "AND p.active = true")
    List<Stock> findLowStockItems();
}