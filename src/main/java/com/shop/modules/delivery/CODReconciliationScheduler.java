package com.shop.modules.delivery;

import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import com.shop.modules.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.shop.modules.billing.Bill;
import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class CODReconciliationScheduler {

    private final ReconciliationService reconciliationService;
    private final DeliveryRepository deliveryRepository;
    private final UserRepository userRepository;
    private final CODWhatsAppService codWhatsAppService;

    // ── Run tracking fields ──
    private LocalDateTime lastEodRunTime;
    private String lastEodRunStatus = "Never run";
    private LocalDateTime lastEscalationRunTime;
    private String lastEscalationRunStatus = "Never run";

    /**
     * Escalation Monitor: Runs every 10 minutes (600,000 milliseconds)
     * Alert managers/admins if any delivery is in OUT status for more than 4 hours.
     */
    @Scheduled(fixedDelay = 600000)
    public void checkUnrecordedPayments() {
        log.info("Running 10-minute escalation check for outstanding deliveries...");
        String errorMsg = null;
        try {
            LocalDateTime threshold = LocalDateTime.now().minusHours(4);
            List<Delivery> outstanding = deliveryRepository.findOutstandingDeliveries(threshold);

            if (!outstanding.isEmpty()) {
                log.warn("Found {} outstanding deliveries older than 4 hours!", outstanding.size());
                
                // Build summary message
                StringBuilder sb = new StringBuilder();
                sb.append("📦 *COD Alert — ").append(outstanding.size()).append(" deliveries pending*\n\n");
                
                BigDecimal totalAmount = BigDecimal.ZERO;
                int idx = 1;
                for (Delivery delivery : outstanding) {
                    Bill bill = delivery.getBill();
                    String customerName = "Unknown";
                    String areaName = "N/A";
                    BigDecimal amount = BigDecimal.ZERO;
                    if (bill != null) {
                        amount = bill.getGrandTotal() != null ? bill.getGrandTotal() : BigDecimal.ZERO;
                        totalAmount = totalAmount.add(amount);
                        if (bill.getCustomer() != null) {
                            customerName = bill.getCustomer().getShopName() != null ? bill.getCustomer().getShopName() : bill.getCustomer().getName();
                            if (bill.getCustomer().getArea() != null) {
                                areaName = bill.getCustomer().getArea().getName();
                            }
                        }
                    }
                    sb.append(idx++).append(". ").append(customerName).append(" — ₹").append(String.format("%.2f", amount)).append(" (").append(areaName).append(")\n");
                }
                
                sb.append("\n💰 *Total Pending: ₹").append(String.format("%.2f", totalAmount)).append("*\n");
                sb.append("🏪 Lari Traders | ").append(java.time.LocalDate.now().toString()).append(" ").append(java.time.LocalTime.now().toString().substring(0, 5));
                String summaryMessage = sb.toString();

                List<User> adminsAndManagers = userRepository.findByRoleIn(List.of(UserRole.ADMIN, UserRole.MANAGER));

                for (User recipient : adminsAndManagers) {
                    if (recipient.getPhone() != null && !recipient.getPhone().isEmpty()) {
                        // Cooldown logic: Check if recipient lastWhatsappAlertSent was sent in last 1 hour
                        LocalDateTime lastSent = recipient.getLastWhatsappAlertSent();
                        if (lastSent != null && lastSent.isAfter(LocalDateTime.now().minusHours(1))) {
                            log.info("Skipping alert for manager {} due to cooldown (last sent at {})", recipient.getName(), lastSent);
                            continue;
                        }
                        
                        codWhatsAppService.sendSummaryEscalationAlert(summaryMessage, recipient.getPhone());
                        
                        recipient.setLastWhatsappAlertSent(LocalDateTime.now());
                        userRepository.save(recipient);
                    }
                }
            }
        } catch (Exception e) {
            errorMsg = e.getMessage();
            log.error("Error in 10-minute outstanding delivery scheduler check: {}", e.getMessage());
        }
        lastEscalationRunTime = LocalDateTime.now();
        lastEscalationRunStatus = errorMsg != null ? "Failed: " + errorMsg : "Success";
    }

    /**
     * EOD Daily Report: Runs at 8:00 PM daily (cron = "0 0 20 * * *")
     * Compiles EOD collection reconciliation records and sends summary statements via WhatsApp.
     */
    @Scheduled(cron = "0 0 20 * * *")
    public void sendEODReport() {
        log.info("Running 8:00 PM EOD reconciliation scheduler...");
        String errorMsg = null;
        try {
            LocalDate today = LocalDate.now();
            
            // 1. Generate reconciliations for today
            reconciliationService.generateEodReconciliations(today);

            // 2. Fetch and broadcast report to admins & managers
            List<DailyReconciliation> recons = reconciliationService.getReconciliationsByDate(today);
            List<User> adminsAndManagers = userRepository.findByRole(UserRole.ADMIN);
            adminsAndManagers.addAll(userRepository.findByRole(UserRole.MANAGER));

            for (DailyReconciliation recon : recons) {
                User boy = userRepository.findById(recon.getDeliveryBoyId()).orElse(null);
                String boyName = boy != null ? boy.getName() : "Unknown";

                for (User recipient : adminsAndManagers) {
                    if (recipient.getPhone() != null && !recipient.getPhone().isEmpty()) {
                        codWhatsAppService.sendDailyReconciliationReport(recon, boyName, recipient.getPhone());
                    }
                }
            }
            log.info("EOD reconciliation report successfully sent to all managers.");
        } catch (Exception e) {
            errorMsg = e.getMessage();
            log.error("Error in EOD reconciliation scheduler: {}", e.getMessage());
        }
        lastEodRunTime = LocalDateTime.now();
        lastEodRunStatus = errorMsg != null ? "Failed: " + errorMsg : "Success";
    }

    public void runEodNow() {
        sendEODReport();
    }

    public void runEscalationNow() {
        checkUnrecordedPayments();
    }

    public java.util.Map<String, Object> getStatus() {
        return java.util.Map.of(
            "enabled", true,
            "cronExpression", "0 0 20 * * *",
            "lastRunTime", lastEodRunTime != null ? lastEodRunTime.toString() : "",
            "lastRunStatus", lastEodRunStatus,
            "lastEscalationRunTime", lastEscalationRunTime != null ? lastEscalationRunTime.toString() : "",
            "lastEscalationRunStatus", lastEscalationRunStatus
        );
    }
}
