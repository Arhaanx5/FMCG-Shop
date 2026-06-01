package com.shop.modules.customer;

import com.shop.modules.customer.dto.AiReminderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiReminderService {

    private final CustomerService customerService;
    private final AiReminderGenerator aiReminderGenerator;

    public AiReminderResponse generateCustomerReminder(String idOrCode) {
        Customer customer = customerService.findCustomerByIdentifier(idOrCode);

        if (customer.getTotalPending() == null || customer.getTotalPending().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Customer has no outstanding balance: " + customer.getName());
        }

        String currentDate = LocalDate.now().toString();
        
        // This delegation avoids AOP self-invocation issues and properly intercepts the call for caching
        String message = aiReminderGenerator.generateReminderMessage(
                customer.getId(),
                customer.getTotalPending(),
                currentDate,
                customer.getName(),
                customer.getShopName()
        );

        String whatsappLink = buildWhatsappLink(customer.getPhone(), message);

        return AiReminderResponse.builder()
                .message(message)
                .whatsappLink(whatsappLink)
                .build();
    }

    private String buildWhatsappLink(String phone, String text) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        // Ensure only digits, and format to 10 digit Indian number prefix if needed
        String digitsOnly = phone.replaceAll("[^0-9]", "");
        String formattedPhone = digitsOnly.length() == 10 ? "91" + digitsOnly : digitsOnly;
        
        return "https://api.whatsapp.com/send?phone=" + formattedPhone + "&text=" + encodeUrl(text);
    }

    private String encodeUrl(String text) {
        try {
            return java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8.toString())
                    .replace("+", "%20");
        } catch (Exception e) {
            log.error("Failed to URL encode reminder message: {}", e.getMessage());
            return "";
        }
    }
}
