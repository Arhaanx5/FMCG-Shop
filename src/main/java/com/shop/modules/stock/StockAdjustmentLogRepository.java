package com.shop.modules.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockAdjustmentLogRepository extends JpaRepository<StockAdjustmentLog, UUID> {
    List<StockAdjustmentLog> findAllByOrderByTimestampDesc();
    Page<StockAdjustmentLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
