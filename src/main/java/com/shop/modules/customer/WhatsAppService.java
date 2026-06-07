package com.shop.modules.customer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppService {

    private final CustomerService customerService;
    private final AiReminderGenerator aiReminderGenerator;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String nodeServiceUrl = "http://127.0.0.1:3000";

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

    public void startBulkSending(List<String> customerIds) {
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

                    // Rate limiting delay (2.5 seconds) to avoid spam limits
                    Thread.sleep(2500);

                } catch (Exception e) {
                    log.error("Failed to send bulk reminder for customer id {}: {}", id, e.getMessage());
                    incrementFailed();
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
}

