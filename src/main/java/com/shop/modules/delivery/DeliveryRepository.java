package com.shop.modules.delivery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

       List<Delivery> findByDeliveryBoyId(UUID boyId);

       List<Delivery> findByDeliveryBoyIdOrderByCreatedAtDesc(UUID boyId);

       @Query("SELECT d FROM Delivery d WHERE " +
                     "d.deliveryBoy.id = :boyId AND " +
                     "CAST(d.createdAt AS date) = :date")
       List<Delivery> findByDeliveryBoyAndDate(
                     @Param("boyId") UUID boyId,
                     @Param("date") LocalDate date);

       List<Delivery> findByStatus(DeliveryStatus status);

       List<Delivery> findByDeliveryBoyIdAndStatus(UUID boyId, DeliveryStatus status);

       List<Delivery> findByDeliveryBoyIdAndStatusIn(UUID boyId, List<DeliveryStatus> statuses);

       List<Delivery> findAllByOrderByCreatedAtDesc();

       long countByStatus(DeliveryStatus status);

       long countByDeliveryBoyIdAndStatus(UUID boyId, DeliveryStatus status);

       boolean existsByBillIdAndStatusIn(UUID billId, List<DeliveryStatus> statuses);

       @Query("SELECT d FROM Delivery d WHERE d.status = 'OUT' AND d.dispatchedAt < :threshold")
       List<Delivery> findOutstandingDeliveries(@Param("threshold") java.time.LocalDateTime threshold);

       List<Delivery> findByBillId(UUID billId);
}