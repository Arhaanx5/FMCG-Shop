package com.shop.modules.khata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByCustomerIdOrderByPaidAtDesc(UUID customerId);

    List<Payment> findByCollectedById(UUID salesmanId);

    List<Payment> findByCollectedByIdOrderByPaidAtDesc(UUID salesmanId);

    @Query("SELECT p FROM Payment p WHERE p.collectedBy.id = :salesmanId AND p.paidAt BETWEEN :start AND :end ORDER BY p.paidAt DESC")
    List<Payment> findByCollectedByIdAndBetween(
            @Param("salesmanId") UUID salesmanId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT p FROM Payment p WHERE p.bill.id IN :billIds")
    List<Payment> findByBillIdIn(@Param("billIds") List<UUID> billIds);

    List<Payment> findAllByOrderByPaidAtDesc();

    @Query("SELECT p FROM Payment p WHERE " +
            "p.paidAt BETWEEN :start AND :end ORDER BY p.paidAt DESC")
    List<Payment> findBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE " +
            "p.customer.id = :customerId")
    BigDecimal getTotalPaidByCustomer(
            @Param("customerId") UUID customerId);
}