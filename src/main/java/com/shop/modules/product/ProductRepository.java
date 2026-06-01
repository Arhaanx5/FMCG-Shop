package com.shop.modules.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProductRepository
        extends JpaRepository<Product, UUID> {

    List<Product> findByActiveTrue();

    Page<Product> findByActiveTrue(Pageable pageable);

    List<Product> findByCategory(Category category);

    List<Product> findByNameContainingIgnoreCaseAndActiveTrue(
            String name);

    Page<Product> findByNameContainingIgnoreCaseAndActiveTrue(
            String name, Pageable pageable);

    // Low stock — check against secondary units
    @Query("SELECT p FROM Product p " +
            "JOIN Stock s ON s.product.id = p.id " +
            "WHERE s.totalSecondaryUnits " +
            "< p.lowStockAlert " +
            "AND p.active = true")
    List<Product> findLowStockProducts();

    // Find by primary unit type
    List<Product> findByPrimaryUnitAndActiveTrue(
            String primaryUnit);

    // Find by category and active
    List<Product> findByCategoryAndActiveTrue(
            Category category);

    // Check duplicate name
    boolean existsByNameIgnoreCaseAndActiveTrue(
            String name);

    // Find by name exact match
    @Query("SELECT p FROM Product p WHERE " +
            "LOWER(p.name) = LOWER(:name) " +
            "AND p.active = true")
    List<Product> findByNameExact(
            @Param("name") String name);

    java.util.Optional<Product> findByProductCodeIgnoreCase(String productCode);

    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(product_code FROM 6) AS integer)), 0) " +
                   "FROM products " +
                   "WHERE product_code ~ '^PROD-[0-9]+$'", nativeQuery = true)
    Integer findMaxProductSequence();

    boolean existsByNameIgnoreCaseAndBrandIsNullAndActiveTrue(String name);

    boolean existsByNameIgnoreCaseAndBrandIgnoreCaseAndActiveTrue(String name, String brand);
}