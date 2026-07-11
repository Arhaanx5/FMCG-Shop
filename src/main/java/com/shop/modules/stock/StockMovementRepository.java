package com.shop.modules.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Page<StockMovement> findAllByOrderByTimestampDesc(Pageable pageable);

    @Query("SELECT m FROM StockMovement m " +
           "LEFT JOIN FETCH m.product p " +
           "LEFT JOIN FETCH m.batch b " +
           "WHERE (cast(:productId as java.util.UUID) IS NULL OR p.id = :productId) AND " +
           "(cast(:movementType as java.lang.String) IS NULL OR m.movementType = :movementType) AND " +
           "(cast(:start as java.time.LocalDateTime) IS NULL OR m.timestamp >= :start) AND " +
           "(cast(:end as java.time.LocalDateTime) IS NULL OR m.timestamp <= :end) AND " +
           "(COALESCE(:search, '') = '' OR " +
           " LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(p.brand) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(p.productCode) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(b.batchNumber) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY m.timestamp DESC")
    Page<StockMovement> findFilteredMovements(
            @Param("productId") UUID productId,
            @Param("movementType") String movementType,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT m FROM StockMovement m " +
           "LEFT JOIN FETCH m.product p " +
           "LEFT JOIN FETCH m.batch b " +
           "WHERE (cast(:productId as java.util.UUID) IS NULL OR p.id = :productId) AND " +
           "(cast(:movementType as java.lang.String) IS NULL OR m.movementType = :movementType) AND " +
           "(cast(:start as java.time.LocalDateTime) IS NULL OR m.timestamp >= :start) AND " +
           "(cast(:end as java.time.LocalDateTime) IS NULL OR m.timestamp <= :end) AND " +
           "(COALESCE(:search, '') = '' OR " +
           " LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(p.brand) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(p.productCode) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(b.batchNumber) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY m.timestamp DESC")
    List<StockMovement> findAllFilteredMovements(
            @Param("productId") UUID productId,
            @Param("movementType") String movementType,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("search") String search);

    List<StockMovement> findByBatchIdOrderByTimestampAsc(UUID batchId);

    @Query("SELECT m FROM StockMovement m " +
           "LEFT JOIN FETCH m.product p " +
           "LEFT JOIN FETCH m.batch b " +
           "WHERE m.movementType IN ('PURCHASE', 'PURCHASE_TOPUP', 'OFFER_RECEIVE') " +
           "ORDER BY m.timestamp DESC")
    List<StockMovement> findAllPurchaseAndOfferMovements();

    // For monthly inventory flow chart — SALE movements since a given date
    // Narrow query: type filter + timestamp range — both indexed fields
    @Query("SELECT m FROM StockMovement m " +
           "LEFT JOIN FETCH m.product p " +
           "LEFT JOIN FETCH m.batch b " +
           "WHERE m.movementType = 'SALE' " +
           "AND m.timestamp >= :since " +
           "ORDER BY m.timestamp ASC")
    List<StockMovement> findSaleMovementsSince(@Param("since") LocalDateTime since);
}
