package com.shop.modules.delivery;

import com.shop.modules.whatsapp.WhatsAppService;
import com.shop.modules.billing.Bill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class CODWhatsAppService {

    private final WhatsAppService whatsAppService;

    @Value("${app.admin.contact:9450XXXXXX}")
    private String adminContact;

    @Async
    public void sendOtpNotification(Delivery delivery, String otp) {
        try {
            Bill bill = delivery.getBill();
            if (bill == null || bill.getCustomer() == null) {
                return;
            }
            String customerPhone = bill.getCustomer().getPhone();
            if (customerPhone == null || customerPhone.isEmpty()) {
                return;
            }

            String displayName = bill.getCustomer().getShopName();
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = bill.getCustomer().getName();
            }
            if (displayName == null) {
                displayName = "Customer";
            }

            String message = String.format(
                "Dear %s,\n\n" +
                "Lari Traders se aapka order deliver karne ke liye delivery partner nikal chuka hai.\n\n" +
                "📦 *Bill Details:*\n" +
                "• Bill No: %s\n" +
                "• Total Amount: ₹%.2f\n\n" +
                "Delivery boy se apna saman check karke receive karne ke liye, unhe yeh OTP batayein: *%s*\n\n" +
                "*Note: Saman aur amount sahi milne par hi OTP share karein.*\n\n" +
                "— Lari Traders",
                displayName,
                bill.getBillNumber(),
                bill.getGrandTotal(),
                otp
            );

            log.info("Sending OTP dispatch notification to phone {}: {}", customerPhone, message);
            whatsAppService.sendText(customerPhone, message);
        } catch (Exception e) {
            log.error("Failed to send OTP notification: {}", e.getMessage());
        }
    }

    @Async
    public void sendEscalationAlert(Delivery delivery, String managerPhone) {
        try {
            if (managerPhone == null || managerPhone.isEmpty()) {
                return;
            }
            Bill bill = delivery.getBill();
            String billNum = bill != null ? bill.getBillNumber() : "N/A";
            String boyName = delivery.getDeliveryBoy() != null ? delivery.getDeliveryBoy().getName() : "Unknown";
            String custName = (bill != null && bill.getCustomer() != null) ? bill.getCustomer().getName() : "Unknown";

            String message = String.format(
                "⚠️ ALERT: COD Delivery is outstanding or suspicious!\nDelivery ID: %s\nBill: %s\nCustomer: %s\nDelivery Boy: %s\nStatus: %s\nOutstanding for too long. Action required.",
                delivery.getId(),
                billNum,
                custName,
                boyName,
                delivery.getStatus()
            );

            log.info("Sending escalation alert to manager {}: {}", managerPhone, message);
            whatsAppService.sendText(managerPhone, message);
        } catch (Exception e) {
            log.error("Failed to send escalation alert: {}", e.getMessage());
        }
    }

    @Async
    public void sendDailyReconciliationReport(DailyReconciliation reconciliation, String deliveryBoyName, String managerPhone) {
        try {
            if (managerPhone == null || managerPhone.isEmpty()) {
                return;
            }

            String message = String.format(
                "📊 Daily COD Collection Reconciliation Report\nDate: %s\nDelivery Boy: %s\nExpected Collection: ₹%.2f\nSubmitted Collection: ₹%.2f\nGap: ₹%.2f\nStatus: %s\nNotes: %s",
                reconciliation.getDate() != null ? reconciliation.getDate().toString() : LocalDate.now().toString(),
                deliveryBoyName,
                reconciliation.getExpectedCollection(),
                reconciliation.getSubmittedCollection(),
                reconciliation.getGap(),
                reconciliation.getStatus(),
                reconciliation.getAdminNotes() != null ? reconciliation.getAdminNotes() : "No notes"
            );

            log.info("Sending EOD reconciliation report to manager {}: {}", managerPhone, message);
            whatsAppService.sendText(managerPhone, message);
        } catch (Exception e) {
            log.error("Failed to send daily reconciliation report: {}", e.getMessage());
        }
    }

    public void sendSummaryEscalationAlert(String summaryMessage, String managerPhone) {
        try {
            if (managerPhone == null || managerPhone.isEmpty()) {
                return;
            }
            log.info("Sending summary escalation alert to manager {}: {}", managerPhone, summaryMessage);
            whatsAppService.sendText(managerPhone, summaryMessage);
        } catch (Exception e) {
            log.error("Failed to send summary escalation alert: {}", e.getMessage());
        }
    }
}
