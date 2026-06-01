package com.shop.modules.customer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.util.UUID;

@Component
@Slf4j
public class AiReminderGenerator {

    @Value("${app.gemini.api-key:}")
    private String apiKey;

    @Cacheable(value = "aiReminders", key = "#customerId.toString() + '_' + #pendingAmount.toString() + '_' + #currentDate")
    public String generateReminderMessage(UUID customerId, BigDecimal pendingAmount, String currentDate, String name, String shopName) {
        log.info("Cache miss for customer {}. Generating fresh AI Hinglish reminder from Gemini API.", customerId);
        
        String prompt = "You are a friendly and polite Indian FMCG distributor assistant. " +
                "Generate a respectful, professional, and friendly payment reminder message in Hinglish (Hindi written in English script) " +
                "for the customer named: " + name + (shopName != null && !shopName.isBlank() ? " of shop: " + shopName : "") + ". " +
                "Their outstanding pending balance is Rs. " + pendingAmount + ". " +
                "Keep the tone extremely polite, warm, and cooperative (using words like 'ji', 'pranam', 'namaskar', 'request', 'clear'). " +
                "Do NOT use placeholder tags (like [date], [your name]). " +
                "Output ONLY the final message content, and absolutely nothing else. Keep it under 150 words.";

        // Use system environment fallback if property not set
        String activeKey = (apiKey == null || apiKey.trim().isEmpty()) ? System.getenv("GEMINI_API_KEY") : apiKey;

        try {
            if (activeKey == null || activeKey.trim().isEmpty()) {
                log.info("Gemini API key not found. Using local Hinglish template fallback.");
                return generateLocalFallback(name, shopName, pendingAmount);
            }

            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + activeKey;
            
            String jsonRequest = "{\n" +
                    "  \"contents\": [{\n" +
                    "    \"parts\": [{\n" +
                    "      \"text\": \"" + escapeJsonString(prompt) + "\"\n" +
                    "    }]\n" +
                    "  }]\n" +
                    "}";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(jsonRequest, headers);
            RestTemplate restTemplate = new RestTemplate();
            
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(4000); 
            factory.setReadTimeout(6000);    
            restTemplate.setRequestFactory(factory);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String text = extractTextFromGeminiResponse(response.getBody());
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
        } catch (Exception e) {
            log.warn("Gemini API call failed, falling back to local Hinglish template. Error: {}", e.getMessage());
        }
        return generateLocalFallback(name, shopName, pendingAmount);
    }

    private String generateLocalFallback(String name, String shopName, BigDecimal pendingAmount) {
        String shopDetail = (shopName != null && !shopName.isBlank()) ? " (" + shopName + ")" : "";
        return "Pranam " + name + " ji" + shopDetail + ",\n\n" +
               "FMCG Shop se namaskar. Aapka outstanding balance Rs. " + pendingAmount + " hai. " +
               "Aapse nivedan hai ki kripya jald se jald iska payment clear karein taaki humara business smooth chalta rahe.\n\n" +
               "Kisi bhi query ke liye aap hume call kar sakte hain. Dhan्यवाद!";
    }

    private String escapeJsonString(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String extractTextFromGeminiResponse(String json) {
        try {
            int textIndex = json.indexOf("\"text\":");
            if (textIndex != -1) {
                int startQuote = json.indexOf("\"", textIndex + 7);
                if (startQuote != -1) {
                    int endQuote = json.indexOf("\"", startQuote + 1);
                    while (endQuote != -1 && json.charAt(endQuote - 1) == '\\') {
                        endQuote = json.indexOf("\"", endQuote + 1);
                    }
                    if (endQuote != -1) {
                        String rawText = json.substring(startQuote + 1, endQuote);
                        return rawText.replace("\\n", "\n")
                                      .replace("\\\"", "\"")
                                      .replace("\\\\", "\\");
                    }
                }
            }
        } catch (Exception e) {
            // Ignore and fallback
        }
        return null;
    }
}
