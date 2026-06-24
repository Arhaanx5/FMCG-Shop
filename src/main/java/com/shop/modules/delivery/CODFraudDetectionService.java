package com.shop.modules.delivery;

import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import com.shop.modules.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CODFraudDetectionService {

    private final CODAuditLogRepository codAuditLogRepository;
    private final UserRepository userRepository;
    private final CODWhatsAppService codWhatsAppService;

    @Async
    public void checkSuspiciousPattern(Delivery delivery) {
        try {
            if (delivery.getDeliveryBoy() == null) {
                return;
            }

            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            LocalDateTime endOfDay = startOfDay.plusDays(1);

            // Fetch audit logs for this delivery boy today
            List<CODAuditLog> todayLogs = codAuditLogRepository.findByDeliveryBoyIdAndTimestampBetween(
                delivery.getDeliveryBoy().getId(), startOfDay, endOfDay
            );

            long defaultCount = todayLogs.stream()
                .filter(log -> "CONVERT_TO_UDHAR".equals(log.getEvent()))
                .count();

            // If a delivery boy converts more than 2 COD bills to UDHAR in a single day, trigger an alert
            if (defaultCount >= 3) {
                log.warn("Suspicious pattern detected: Delivery boy {} has converted {} COD bills to UDHAR today!",
                    delivery.getDeliveryBoy().getName(), defaultCount);

                // Fetch admins/managers to alert them
                List<User> adminsAndManagers = userRepository.findByRole(UserRole.ADMIN);
                adminsAndManagers.addAll(userRepository.findByRole(UserRole.MANAGER));

                for (User recipient : adminsAndManagers) {
                    if (recipient.getPhone() != null && !recipient.getPhone().isEmpty()) {
                        codWhatsAppService.sendEscalationAlert(delivery, recipient.getPhone());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error in fraud detection pattern checks: {}", e.getMessage());
        }
    }
}
