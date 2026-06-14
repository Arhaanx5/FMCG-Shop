package com.shop.modules.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockRepository
        extends JpaRepository<Stock, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.product.id = :productId")
    Optional<Stock> findByProductIdWithLock(@Param("productId") UUID productId);

    Optional<Stock> findByProductId(UUID productId);

    Page<Stock> findAll(Pageable pageable);

    // Low stock items using new field name
    @Query("SELECT s FROM Stock s " +
            "JOIN FETCH s.product p " +
            "WHERE s.totalSecondaryUnits " +
            "< p.lowStockAlert " +
            "AND p.active = true")
    List<Stock> findLowStockItems();
}