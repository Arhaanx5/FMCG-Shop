package com.shop.modules.customer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppService {

    private final CustomerService customerService;
    private final AiReminderGenerator aiReminderGenerator;
    private final com.shop.modules.receivables.UdharReminderLogRepository udharReminderLogRepository;
    private final com.shop.modules.user.UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String nodeServiceUrl = "http://127.0.0.1:3000";

    @org.springframework.beans.factory.annotation.Value("${app.whatsapp.internal-secret:}")
    private String internalSecret;

    @jakarta.annotation.PostConstruct
    public void init() {
        if (internalSecret != null && !internalSecret.isEmpty()) {
            restTemplate.getInterceptors().add((request, body, execution) -> {
                request.getHeaders().add("x-internal-secret", internalSecret);
                return execution.execute(request, body);
            });
        }
    }

    // Progress State
    private final Map<String, Object> progress = new ConcurrentHashMap<>();

    {
        resetProgress();
    }

    private void resetProgress() {
        progress.put("isSending", false);
        progress.put("total", 0);
        progress.put("processed", 0);
        progress.put("success", 0);
        progress.put("failed", 0);
        progress.put("currentCustomer", "");
    }

    public Map<String, Object> getProgress() {
        return new HashMap<>(progress);
    }

    public String getStatus() {
        try {
            Map<?, ?> response = restTemplate.getForObject(nodeServiceUrl + "/status", Map.class);
            return response != null ? (String) response.get("status") : "DISCONNECTED";
        } catch (Exception e) {
            log.error("Failed to connect to WhatsApp Node service at /status: {}", e.getMessage());
            return "DISCONNECTED";
        }
    }

    public String getQrCode() {
        try {
            Map<?, ?> response = restTemplate.getForObject(nodeServiceUrl + "/qr", Map.class);
            return response != null ? (String) response.get("qr") : null;
        } catch (Exception e) {
            log.error("Failed to fetch QR code from Node service at /qr: {}", e.getMessage());
            return null;
        }
    }

    public void logout() {
        try {
            restTemplate.postForObject(nodeServiceUrl + "/logout", null, Map.class);
            resetProgress();
        } catch (Exception e) {
            log.error("Failed to log out device in Node service at /logout: {}", e.getMessage());
        }
    }

    public void startBulkSending(List<String> customerIds, String senderPhone) {
        if (Boolean.TRUE.equals(progress.get("isSending"))) {
            log.warn("Bulk sending is already in progress.");
            return;
        }

        progress.put("isSending", true);
        progress.put("total", customerIds.size());
        progress.put("processed", 0);
        progress.put("success", 0);
        progress.put("failed", 0);

        log.info("Starting background bulk reminders sending for {} customers", customerIds.size());

        CompletableFuture.runAsync(() -> {
            for (String id : customerIds) {
                try {
                    Customer customer = customerService.findCustomerByIdentifier(id);
                    if (customer.getTotalPending() == null || customer.getTotalPending().compareTo(BigDecimal.ZERO) <= 0) {
                        incrementFailed();
                        continue;
                    }

                    progress.put("currentCustomer", customer.getName());

                    // Generate reminder message (reusing the Custom Hinglish formatting)
                    String message = aiReminderGenerator.generateReminderMessage(
                            customer.getId(),
                            customer.getTotalPending(),
                            LocalDate.now().toString(),
                            customer.getName(),
                            customer.getShopName()
                    );

                    // Call Node Service to send message
                    Map<String, String> payload = new HashMap<>();
                    payload.put("phone", customer.getPhone());
                    payload.put("message", message);

                    restTemplate.postForObject(nodeServiceUrl + "/send", payload, Map.class);
                    incrementSuccess();

                    // Log success to DB
                    try {
                        com.shop.modules.user.User sender = senderPhone != null ? userRepository.findByPhone(senderPhone).orElse(null) : null;
                        com.shop.modules.receivables.UdharReminderLog logEntity = com.shop.modules.receivables.UdharReminderLog.builder()
                                .customer(customer)
                                .channel("WHATSAPP")
                                .status("SENT")
                                .notes("Bulk automated WhatsApp reminder sent successfully.")
                                .createdBy(sender)
                                .reminderSentAt(LocalDateTime.now())
                                .build();
                        udharReminderLogRepository.save(logEntity);
                    } catch (Exception le) {
                        log.error("Failed to save bulk reminder log: {}", le.getMessage());
                    }

                    // Rate limiting delay (2.5 seconds) to avoid spam limits
                    Thread.sleep(2500);

                } catch (Exception e) {
                    log.error("Failed to send bulk reminder for customer id {}: {}", id, e.getMessage());
                    incrementFailed();

                    // Log failure to DB
                    try {
                        Customer customer = customerService.findCustomerByIdentifier(id);
                        com.shop.modules.user.User sender = senderPhone != null ? userRepository.findByPhone(senderPhone).orElse(null) : null;
                        com.shop.modules.receivables.UdharReminderLog logEntity = com.shop.modules.receivables.UdharReminderLog.builder()
                                .customer(customer)
                                .channel("WHATSAPP")
                                .status("FAILED")
                                .notes("Bulk automated WhatsApp reminder failed: " + e.getMessage())
                                .createdBy(sender)
                                .reminderSentAt(LocalDateTime.now())
                                .build();
                        udharReminderLogRepository.save(logEntity);
                    } catch (Exception le) {
                        log.error("Failed to save failed bulk reminder log: {}", le.getMessage());
                    }
                }
            }

            progress.put("isSending", false);
            progress.put("currentCustomer", "Completed");
        });
    }

    private void incrementSuccess() {
        progress.put("processed", (int) progress.get("processed") + 1);
        progress.put("success", (int) progress.get("success") + 1);
    }

    private void incrementFailed() {
        progress.put("processed", (int) progress.get("processed") + 1);
        progress.put("failed", (int) progress.get("failed") + 1);
    }

    public void sendMedia(String phone, String base64Media, String filename, String caption) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("phone", phone);
            payload.put("media", base64Media);
            payload.put("filename", filename);
            payload.put("caption", caption);

            restTemplate.postForObject(nodeServiceUrl + "/send-media", payload, Map.class);
            log.info("Media message sent successfully to {} via Node helper", phone);
        } catch (Exception e) {
            log.error("Failed to send media message to {} via Node helper: {}", phone, e.getMessage());
            throw new RuntimeException("WhatsApp media delivery failed: " + e.getMessage());
        }
    }

    public void sendText(String phone, String message) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("phone", phone);
            payload.put("message", message);

            restTemplate.postForObject(nodeServiceUrl + "/send", payload, Map.class);
            log.info("Text message sent successfully to {} via Node helper", phone);
        } catch (Exception e) {
            log.error("Failed to send text message to {} via Node helper: {}", phone, e.getMessage());
            throw new RuntimeException("WhatsApp text delivery failed: " + e.getMessage());
        }
    }

    public String generateInvoicePdf(String html) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("html", html);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                nodeServiceUrl + "/generate-pdf", payload, Map.class);

            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                throw new RuntimeException("PDF generation returned failure from Node service");
            }
            log.info("Invoice PDF generated successfully via Puppeteer");
            return (String) response.get("pdf"); // base64 string
        } catch (Exception e) {
            log.error("Invoice PDF generation failed via Node service: {}", e.getMessage());
            throw new RuntimeException("Invoice PDF generation failed: " + e.getMessage());
        }
    }
}

