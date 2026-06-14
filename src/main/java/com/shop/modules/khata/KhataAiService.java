package com.shop.modules.khata;

import com.shop.modules.customer.Customer;
import com.shop.modules.customer.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KhataAiService {

    private final CustomerRepository customerRepository;
    private static final String PYTHON_TEXT_GEN_URL = "http://127.0.0.1:8087/ocr/generate-text";

    public String generateReminder(UUID customerId, String language) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

        String sanitizedName = customer.getName().replaceAll("[\\[\\]<>{}]", "").trim();
        String sanitizedShopName = customer.getShopName() != null ? customer.getShopName().replaceAll("[\\[\\]<>{}]", "").trim() : "Retail Store";

        boolean isEnglish = "ENGLISH".equalsIgnoreCase(language);

        StringBuilder sb = new StringBuilder();
        if (isEnglish) {
            sb.append("Write a friendly, polite, yet professional business WhatsApp payment reminder in English for a wholesale retailer client. ")
              .append("Keep it brief (3-4 sentences max), use professional retail terminology (e.g. 'outstanding balance', 'due amount', 'next delivery schedule'), and use emojis.\n\n");
        } else {
            sb.append("Write a friendly, polite, yet professional business WhatsApp payment reminder in Hinglish (Hindi written in English/Latin/Roman script) for a wholesale retailer client. ")
              .append("Keep it brief (3-4 sentences max), use friendly retail terminology (e.g. 'Khata book', 'Udhar balance', 'Next delivery schedule'), and use emojis. Do NOT use Devanagari Hindi characters.\n\n");
        }

        sb.append("CUSTOMER DETAILS:\n")
          .append("- Name: ").append(sanitizedName).append("\n")
          .append("- Shop Name: ").append(sanitizedShopName).append("\n")
          .append("- Pending Amount: Rs. ").append(customer.getTotalPending()).append("\n\n")
          .append("Draft message showing clear outstanding balance, mentioning online modes (UPI, QR) or cash collection options, and wishing them good business. Do not add placeholders. Write the final text directly.");

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("prompt", sb.toString());
            
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(PYTHON_TEXT_GEN_URL, entity, Map.class);
            if (response != null && response.containsKey("text")) {
                return (String) response.get("text");
            }
            return "Failed to generate AI reminder text.";
        } catch (Exception e) {
            log.error("Error generating reminder draft", e);
            return "Error generating reminder draft: " + e.getMessage();
        }
    }
}
