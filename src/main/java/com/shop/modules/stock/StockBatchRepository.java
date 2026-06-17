package com.shop.modules.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
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
}