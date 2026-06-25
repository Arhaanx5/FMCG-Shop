package com.shop.modules.billing;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface BillRepository extends JpaRepository<Bill, UUID> {

    @Override
    @EntityGraph(attributePaths = {"customer", "createdBy", "items.product"})
    List<Bill> findAll();

    @EntityGraph(attributePaths = {"customer", "createdBy", "items.product"})
    List<Bill> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    @EntityGraph(attributePaths = {"customer", "createdBy", "items.product"})
    @Query("SELECT b FROM Bill b WHERE " +
           "b.pendingAmount > 0 AND (b.status = 'CONFIRMED' OR b.status = 'PARTIAL') " +
           "ORDER BY b.createdAt DESC")
    List<Bill> findPendingBills();
    @Query("SELECT COALESCE(MAX(" +
           "CAST(SUBSTRING(b.billNumber, 6) AS int)" +
           "), 0) FROM Bill b WHERE b.billNumber LIKE 'BILL-%'")
    Integer findMaxBillSequence();

    @Query(value = "SELECT nextval('bill_number_seq')", nativeQuery = true)
    Long getNextBillSequence();

    @EntityGraph(attributePaths = {"customer", "createdBy", "items.product"})
    @Query("SELECT b FROM Bill b WHERE " +
           "(:status IS NULL OR b.status = :status) AND " +
           "(:excludeDrafts = false OR b.status <> 'DRAFT') AND " +
           "(:salesmanId IS NULL OR b.createdBy.id = :salesmanId) AND " +
           "(:search IS NULL OR LOWER(b.billNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "                  LOWER(b.customer.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "                  LOWER(b.customer.shopName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "                  LOWER(b.customer.phone) LIKE LOWER(CONCAT('%', :search, '%')))")
    org.springframework.data.domain.Page<Bill> findBillsPaged(
            @Param("status") BillStatus status,
            @Param("excludeDrafts") boolean excludeDrafts,
            @Param("salesmanId") UUID salesmanId,
            @Param("search") String search,
            org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "createdBy", "items.product"})
    @Query("SELECT b FROM Bill b WHERE " +
           "b.customer.id = :customerId AND " +
           "(:salesmanId IS NULL OR b.createdBy.id = :salesmanId)")
    org.springframework.data.domain.Page<Bill> findCustomerHistoryPaged(
            @Param("customerId") UUID customerId,
            @Param("salesmanId") UUID salesmanId,
            org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "createdBy", "items.product"})
    @Query("SELECT b FROM Bill b WHERE " +
           "b.createdAt BETWEEN :start AND :end")
    List<Bill> findBillsBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    List<Bill> findByCreatedByIdAndStatus(UUID salesmanId, BillStatus status);

    List<Bill> findByCreatedByIdAndStatusIn(UUID salesmanId, List<BillStatus> statuses);

    List<Bill> findByStatusIn(List<BillStatus> statuses);

    @EntityGraph(attributePaths = {"customer", "createdBy", "items.product"})
    List<Bill> findTop5ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"customer", "createdBy", "items.product"})
    @Query("SELECT b FROM Bill b WHERE b.status <> 'CANCELLED' ORDER BY b.createdAt DESC")
    List<Bill> findRecentBills(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT COALESCE(SUM(b.paidAmount), 0) FROM Bill b WHERE b.customer.id = :customerId AND b.status IN ('CONFIRMED', 'PARTIAL', 'PAID')")
    java.math.BigDecimal sumPaidAmountByCustomerId(@Param("customerId") UUID customerId);

    @Query("SELECT MAX(b.createdAt) FROM Bill b JOIN b.items i WHERE i.product.id = :productId AND b.status <> 'CANCELLED'")
    java.time.LocalDateTime findLastSaleDateForProduct(@Param("productId") UUID productId);

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

    @EntityGraph(attributePaths = {"items.product", "items.batch"})
    org.springframework.data.domain.Page<Bill> findByStatusAndCreatedAtBefore(
        BillStatus status,
        LocalDateTime cutoff,
        org.springframework.data.domain.Pageable pageable
    );
}