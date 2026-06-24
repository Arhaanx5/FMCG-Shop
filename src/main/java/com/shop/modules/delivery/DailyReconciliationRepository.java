package com.shop.modules.delivery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyReconciliationRepository extends JpaRepository<DailyReconciliation, UUID> {
    Optional<DailyReconciliation> findByDeliveryBoyIdAndDate(UUID deliveryBoyId, LocalDate date);
    List<DailyReconciliation> findByDate(LocalDate date);
    List<DailyReconciliation> findByDeliveryBoyIdOrderByDateDesc(UUID deliveryBoyId);
}
