package com.shop.modules.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockBatchRepository
        extends JpaRepository<StockBatch, UUID> {

    // FIFO — oldest expiry first
    @Query("SELECT b FROM StockBatch b " +
            "WHERE b.product.id = :productId " +
            "AND b.exhausted = false " +
            "ORDER BY b.expiryDate ASC, " +
            "b.receivedAt ASC")
    List<StockBatch> findActiveBatchesFIFO(
            @Param("productId") UUID productId);

    List<StockBatch> findByProductId(UUID productId);

    List<StockBatch> findByProductIdOrderByReceivedAtDesc(UUID productId);

    // Expiring within given date
    @Query("SELECT b FROM StockBatch b " +
            "WHERE b.expiryDate <= :date " +
            "AND b.exhausted = false " +
            "ORDER BY b.expiryDate ASC")
    List<StockBatch> findExpiringBefore(
            @Param("date") LocalDate date);

    boolean existsByBatchNumberIgnoreCase(String batchNumber);

    boolean existsByProductIdAndBatchNumberIgnoreCase(UUID productId, String batchNumber);

    boolean existsBySupplierNameIgnoreCaseAndInvoiceNumberIgnoreCase(String supplierName, String invoiceNumber);

    List<StockBatch> findByInvoiceNumberIgnoreCase(String invoiceNumber);

    boolean existsByExhaustedFalse();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM StockBatch b WHERE b.id = :id")
    Optional<StockBatch> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT b FROM StockBatch b WHERE b.receivedAt >= :start AND b.receivedAt < :end ORDER BY b.receivedAt DESC")
    List<StockBatch> findByReceivedAtBetweenOrderByReceivedAtDesc(
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    @Query(value = "SELECT b FROM StockBatch b " +
                   "JOIN FETCH b.product p " +
                   "WHERE LOWER(p.name) LIKE :query " +
                   "OR LOWER(b.batchNumber) LIKE :query " +
                   "OR LOWER(b.supplierName) LIKE :query " +
                   "OR LOWER(b.invoiceNumber) LIKE :query",
           countQuery = "SELECT COUNT(b) FROM StockBatch b " +
                        "JOIN b.product p " +
                        "WHERE LOWER(p.name) LIKE :query " +
                        "OR LOWER(b.batchNumber) LIKE :query " +
                        "OR LOWER(b.supplierName) LIKE :query " +
                        "OR LOWER(b.invoiceNumber) LIKE :query")
    Page<StockBatch> searchBatches(
            @Param("query") String query,
            Pageable pageable);
}