package com.shop.modules.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Page<StockMovement> findAllByOrderByTimestampDesc(Pageable pageable);

    @Query("SELECT m FROM StockMovement m WHERE " +
           "(cast(:productId as String) IS NULL OR m.product.id = :productId) AND " +
           "(cast(:movementType as String) IS NULL OR m.movementType = :movementType) AND " +
           "(cast(:start as String) IS NULL OR m.timestamp >= :start) AND " +
           "(cast(:end as String) IS NULL OR m.timestamp <= :end) " +
           "ORDER BY m.timestamp DESC")
    Page<StockMovement> findFilteredMovements(
            @Param("productId") UUID productId,
            @Param("movementType") String movementType,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);
}
