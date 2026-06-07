package com.shop.modules.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface BillRepository extends JpaRepository<Bill, UUID> {

    List<Bill> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    @Query("SELECT b FROM Bill b WHERE " +
           "b.pendingAmount > 0 AND (b.status = 'CONFIRMED' OR b.status = 'PARTIAL') " +
           "ORDER BY b.createdAt DESC")
    List<Bill> findPendingBills();

    @Query("SELECT COALESCE(MAX(" +
           "CAST(SUBSTRING(b.billNumber, 6) AS int)" +
           "), 0) FROM Bill b WHERE b.billNumber LIKE 'BILL-%'")
    Integer findMaxBillSequence();

    @Query("SELECT b FROM Bill b WHERE " +
           "b.createdAt BETWEEN :start AND :end")
    List<Bill> findBillsBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    List<Bill> findByCustomerId(UUID customerId);

    List<Bill> findByCreatedByIdAndStatus(UUID salesmanId, BillStatus status);

    @Query("SELECT COALESCE(SUM(b.paidAmount), 0) FROM Bill b WHERE b.customer.id = :customerId AND b.status IN ('CONFIRMED', 'PARTIAL', 'PAID')")
    java.math.BigDecimal sumPaidAmountByCustomerId(@Param("customerId") UUID customerId);

    @Query("SELECT DISTINCT b.customer FROM Bill b WHERE " +
           "b.pendingAmount > 0 AND (b.status = 'CONFIRMED' OR b.status = 'PARTIAL') AND b.createdAt < :cutoff")
    List<com.shop.modules.customer.Customer> findCustomersWithOverdueBills(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Fetch all CONFIRMED or PARTIAL bills for a customer that still have pending amount,
     * ordered oldest-first (FIFO), optionally excluding a specific bill (the source bill).
     */
    @Query("SELECT b FROM Bill b WHERE b.customer.id = :customerId " +
           "AND b.id <> :excludeBillId " +
           "AND b.pendingAmount > 0 " +
           "AND (b.status = 'CONFIRMED' OR b.status = 'PARTIAL') " +
           "ORDER BY b.createdAt ASC")
    List<Bill> findPendingBillsForCustomerExcluding(
            @Param("customerId") UUID customerId,
            @Param("excludeBillId") UUID excludeBillId);
}