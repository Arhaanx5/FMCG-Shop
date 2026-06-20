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
           "coalesce(:productId, m.product.id) = m.product.id AND " +
           "coalesce(:movementType, m.movementType) = m.movementType AND " +
           "coalesce(:start, m.timestamp) <= m.timestamp AND " +
           "coalesce(:end, m.timestamp) >= m.timestamp " +
           "ORDER BY m.timestamp DESC")
    Page<StockMovement> findFilteredMovements(
            @Param("productId") UUID productId,
            @Param("movementType") String movementType,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);
}
