package com.shop.modules.damage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DamageLogRepository extends JpaRepository<DamageLog, UUID> {

    @Query("SELECT d FROM DamageLog d WHERE " +
            "d.loggedAt BETWEEN :start AND :end " +
            "ORDER BY d.loggedAt DESC")
    List<DamageLog> findForMonth(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    List<DamageLog> findByProductId(UUID productId);

    @Query("SELECT SUM(d.valueLoss) FROM DamageLog d WHERE " +
            "d.loggedAt BETWEEN :start AND :end")
    BigDecimal getTotalDamageLoss(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}