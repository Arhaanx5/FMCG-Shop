package com.shop.modules.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findByActiveTrue();

    Page<Customer> findByActiveTrue(Pageable pageable);

    // NOTE: LEFT JOIN FETCH with Pageable causes HHH90003004 (in-memory pagination).
    // Use a plain query here; area will lazy-load in batches via @BatchSize on Customer.area
    @Query(value = "SELECT c FROM Customer c WHERE " +
           "(COALESCE(:search, '') = '' OR " +
           " LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(c.shopName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(c.phone) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(c.customerCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND c.active = :active")
    Page<Customer> findCustomersFiltered(
            @Param("search") String search,
            @Param("active") boolean active,
            Pageable pageable);

    List<Customer> findByNameContainingIgnoreCaseAndActiveTrue(String name);

    Page<Customer> findByNameContainingIgnoreCaseAndActiveTrue(String name, Pageable pageable);

    @Query("SELECT c FROM Customer c LEFT JOIN FETCH c.area WHERE c.lastOrderAt < :cutoff AND c.active = true")
    List<Customer> findInactiveCustomers(
            @Param("cutoff") LocalDateTime cutoff);

    List<Customer> findByAreaId(UUID areaId);

    java.util.Optional<Customer> findByCustomerCodeIgnoreCase(String customerCode);

    @Query("SELECT COALESCE(MAX(" +
           "CAST(SUBSTRING(c.customerCode, 6) AS int)" +
           "), 0) FROM Customer c WHERE c.customerCode LIKE 'CUST-%'")
    Integer findMaxCustomerSequence();

    @Query("SELECT COALESCE(SUM(c.totalPending), 0) FROM Customer c WHERE c.active = true")
    BigDecimal getTotalPendingBalance();

    @Query("SELECT COUNT(c) > 0 FROM Customer c WHERE c.phone = :phone AND c.active = true")
    boolean existsByPhoneAndActiveTrue(@Param("phone") String phone);

    @Query("SELECT COUNT(c) > 0 FROM Customer c WHERE c.phone = :phone AND c.active = true AND c.id <> :excludeId")
    boolean existsByPhoneAndActiveTrueAndIdNot(@Param("phone") String phone, @Param("excludeId") UUID excludeId);

    @Query("SELECT c FROM Customer c WHERE c.active = true AND c.totalPending > 0")
    List<Customer> findActiveCustomersWithPendingBalance();
}