package com.shop.modules.dashboard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HealthReportRepository extends JpaRepository<HealthReport, UUID> {
    Optional<HealthReport> findByReportYearAndReportMonth(int reportYear, int reportMonth);
    List<HealthReport> findAllByOrderByReportYearDescReportMonthDesc(Pageable pageable);
}
