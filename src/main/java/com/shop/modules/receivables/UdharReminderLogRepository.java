package com.shop.modules.receivables;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UdharReminderLogRepository extends JpaRepository<UdharReminderLog, UUID> {
    
    // Get last reminder log for a customer
    Optional<UdharReminderLog> findTopByCustomerIdOrderByReminderSentAtDesc(UUID customerId);

    // Fetch history of reminders for a customer
    List<UdharReminderLog> findByCustomerIdOrderByReminderSentAtDesc(UUID customerId);
}
