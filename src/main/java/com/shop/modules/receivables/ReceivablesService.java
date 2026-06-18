package com.shop.modules.receivables;

import com.shop.modules.billing.Bill;
import com.shop.modules.billing.BillRepository;
import com.shop.modules.customer.AiReminderGenerator;
import com.shop.modules.customer.Customer;
import com.shop.modules.customer.CustomerRepository;
import com.shop.modules.customer.WhatsAppService;
import com.shop.modules.receivables.dto.ReceivablesPendingResponse;
import com.shop.modules.receivables.dto.SendReminderResponse;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceivablesService {

    private final BillRepository billRepository;
    private final CustomerRepository customerRepository;
    private final UdharReminderLogRepository logRepository;
    private final UserRepository userRepository;
    private final WhatsAppService whatsAppService;
    private final AiReminderGenerator aiReminderGenerator;

    public List<ReceivablesPendingResponse> getPendingReceivables(String sortBy) {
        List<Customer> customersWithBalance = customerRepository.findActiveCustomersWithPendingBalance();
        List<Bill> pendingBills = billRepository.findPendingBills();
        
        // Group bills by customer ID
        Map<UUID, List<Bill>> billsByCustomerId = pendingBills.stream()
                .filter(b -> b.getCustomer() != null)
                .collect(Collectors.groupingBy(b -> b.getCustomer().getId()));

        List<ReceivablesPendingResponse> resultList = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Customer customer : customersWithBalance) {
            List<Bill> customerBills = billsByCustomerId.getOrDefault(customer.getId(), Collections.emptyList());

            // Find oldest bill to determine daysOverdue, fallback to customer creation date
            LocalDateTime oldestDate = null;
            UUID oldestBillId = null;

            if (!customerBills.isEmpty()) {
                Bill oldestBill = customerBills.stream()
                        .min(Comparator.comparing(Bill::getCreatedAt))
                        .orElse(null);
                if (oldestBill != null) {
                    oldestDate = oldestBill.getCreatedAt();
                    oldestBillId = oldestBill.getId();
                }
            }

            if (oldestDate == null) {
                oldestDate = customer.getCreatedAt() != null ? customer.getCreatedAt() : LocalDateTime.now();
            }

            int daysOverdue = (int) ChronoUnit.DAYS.between(oldestDate, now);
            BigDecimal totalPending = customer.getTotalPending() != null ? customer.getTotalPending() : BigDecimal.ZERO;

            // Fetch last reminder log info
            Optional<UdharReminderLog> lastLogOpt = logRepository.findTopByCustomerIdOrderByReminderSentAtDesc(customer.getId());
            LocalDateTime lastSentAt = lastLogOpt.map(UdharReminderLog::getReminderSentAt).orElse(null);

            // Determine if it needs follow-up (15+ days overdue, last reminder 7+ days ago or never)
            boolean needsFollowUp = (daysOverdue >= 15) && (lastSentAt == null || lastSentAt.isBefore(now.minusDays(7)));

            resultList.add(ReceivablesPendingResponse.builder()
                    .customerId(customer.getId())
                    .customerName(customer.getName())
                    .shopName(customer.getShopName())
                    .phoneNumber(customer.getPhone())
                    .pendingAmount(totalPending)
                    .daysOverdue(daysOverdue)
                    .lastReminderSentAt(lastSentAt)
                    .billId(oldestBillId)
                    .needsFollowUp(needsFollowUp)
                    .isNpa(customer.getIsNpa() != null ? customer.getIsNpa() : false)
                    .build());
        }

        // Apply sorting
        if ("pendingAmount".equalsIgnoreCase(sortBy)) {
            resultList.sort(Comparator.comparing(ReceivablesPendingResponse::getPendingAmount).reversed());
        } else if ("customerName".equalsIgnoreCase(sortBy)) {
            resultList.sort(Comparator.comparing(ReceivablesPendingResponse::getCustomerName));
        } else {
            // Default to daysOverdue descending
            resultList.sort(Comparator.comparing(ReceivablesPendingResponse::getDaysOverdue).reversed());
        }

        return resultList;
    }

    @Transactional
    public SendReminderResponse sendReminder(UUID customerId, String channel, String notes, String senderPhone, boolean ignoreCooldown) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found: " + customerId));

        // 24-hour Cooldown Spam-Guard Check
        if (!ignoreCooldown) {
            Optional<UdharReminderLog> lastLogOpt = logRepository.findTopByCustomerIdOrderByReminderSentAtDesc(customerId);
            if (lastLogOpt.isPresent()) {
                UdharReminderLog lastLog = lastLogOpt.get();
                if (lastLog.getReminderSentAt() != null && lastLog.getReminderSentAt().isAfter(LocalDateTime.now().minusHours(24))) {
                    long hoursAgo = ChronoUnit.HOURS.between(lastLog.getReminderSentAt(), LocalDateTime.now());
                    return SendReminderResponse.builder()
                            .success(false)
                            .sent(false)
                            .error("COOLDOWN")
                            .message(String.format("Bhai, is customer ko already %d hours pehle reminder bheja ja chuka hai. Kya aap fir se notification bhejna chahte hain?", hoursAgo))
                            .build();
                }
            }
        }

        User sender = senderPhone != null ? userRepository.findByPhone(senderPhone).orElse(null) : null;

        // Find oldest pending bill
        List<Bill> customerPending = billRepository.findPendingBills().stream()
                .filter(b -> b.getCustomer() != null && b.getCustomer().getId().equals(customerId))
                .sorted(Comparator.comparing(Bill::getCreatedAt))
                .collect(Collectors.toList());

        Bill oldestBill = customerPending.isEmpty() ? null : customerPending.get(0);

        String message = aiReminderGenerator.generateReminderMessage(
                customer.getId(),
                customer.getTotalPending(),
                LocalDate.now().toString(),
                customer.getName(),
                customer.getShopName()
        );

        String whatsappLink = buildWhatsappLink(customer.getPhone(), message);
        
        UdharReminderLog logEntity = UdharReminderLog.builder()
                .customer(customer)
                .bill(oldestBill)
                .channel(channel)
                .notes(notes)
                .createdBy(sender)
                .reminderSentAt(LocalDateTime.now())
                .build();

        if ("WHATSAPP".equalsIgnoreCase(channel)) {
            String status = whatsAppService.getStatus();
            if ("CONNECTED".equalsIgnoreCase(status)) {
                try {
                    whatsAppService.sendText(customer.getPhone(), message);
                    logEntity.setStatus("SENT");
                    logEntity.setNotes(notes != null && !notes.isBlank() ? notes : "Automated WhatsApp reminder sent successfully.");
                    logRepository.save(logEntity);
                    return SendReminderResponse.builder()
                            .success(true)
                            .sent(true)
                            .message(message)
                            .build();
                } catch (Exception e) {
                    log.error("Failed to send automated WhatsApp text: {}", e.getMessage());
                    logEntity.setStatus("FAILED");
                    logEntity.setNotes("Automated send failed: " + e.getMessage() + ". " + (notes != null ? notes : ""));
                    logRepository.save(logEntity);
                    return SendReminderResponse.builder()
                            .success(true)
                            .sent(false)
                            .whatsappLink(whatsappLink)
                            .message(message)
                            .error("WhatsApp delivery failed. Please use manual link fallback.")
                            .build();
                }
            } else {
                logEntity.setStatus("FAILED");
                logEntity.setNotes("WhatsApp service not connected. " + (notes != null ? notes : ""));
                logRepository.save(logEntity);
                return SendReminderResponse.builder()
                        .success(true)
                        .sent(false)
                        .whatsappLink(whatsappLink)
                        .message(message)
                        .error("WhatsApp session disconnected. Please use manual link fallback.")
                        .build();
            }
        } else {
            // MANUAL / SMS / CALL
            logEntity.setStatus("MANUAL");
            logEntity.setNotes(notes != null && !notes.isBlank() ? notes : "Manual follow-up initiated.");
            logRepository.save(logEntity);
            return SendReminderResponse.builder()
                    .success(true)
                    .sent(false)
                    .whatsappLink(whatsappLink)
                    .message(message)
                    .build();
        }
    }

    private String buildWhatsappLink(String phone, String text) {
        if (phone == null || phone.isBlank()) return "";
        String digitsOnly = phone.replaceAll("[^0-9]", "");
        String formattedPhone = digitsOnly.length() == 10 ? "91" + digitsOnly : digitsOnly;
        try {
            return "https://api.whatsapp.com/send?phone=" + formattedPhone + "&text=" +
                    java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8.toString()).replace("+", "%20");
        } catch (Exception e) {
            return "";
        }
    }
}
