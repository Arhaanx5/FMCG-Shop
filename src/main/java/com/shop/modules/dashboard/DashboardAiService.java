package com.shop.modules.dashboard;

import com.shop.modules.dashboard.dto.DashboardSummaryResponse;
import com.shop.modules.dashboard.dto.DashboardResponse;
import com.shop.modules.dashboard.dto.MonthlyReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardAiService {

    private final DashboardService dashboardService;
    private static final String PYTHON_TEXT_GEN_URL = "http://127.0.0.1:8087/ocr/generate-text";
    private final Map<String, String> insightsCache = new java.util.concurrent.ConcurrentHashMap<>();

    public String generateInsights(int year, int month) {
        return generateInsights(year, month, false);
    }

    public String generateInsights(int year, int month, boolean forceRefresh) {
        String cacheKey = year + "-" + month;
        if (!forceRefresh && insightsCache.containsKey(cacheKey)) {
            return insightsCache.get(cacheKey);
        }
        DashboardSummaryResponse summary = dashboardService.getDashboardSummary(year, month);
        String prompt = buildPromptForSummary(summary, year, month);
        String insights = callPythonTextGen(prompt);
        if (insights != null && !insights.startsWith("Failed to connect")) {
            insightsCache.put(cacheKey, insights);
        }
        return insights;
    }

    public String chatWithDashboard(String message, int year, int month) {
        String sanitizedMessage = message.replaceAll("[\\[\\]<>{}]", "").trim();
        if (sanitizedMessage.length() > 500) {
            sanitizedMessage = sanitizedMessage.substring(0, 500);
        }
        DashboardSummaryResponse summary = dashboardService.getDashboardSummary(year, month);
        String prompt = buildPromptForChat(summary, sanitizedMessage);
        return callPythonTextGen(prompt);
    }

    private String buildPromptForSummary(DashboardSummaryResponse summary, int year, int month) {
        DashboardResponse today = summary.getToday();
        MonthlyReportResponse monthly = summary.getMonthly();

        StringBuilder sb = new StringBuilder();
        sb.append("Analyze the following metrics for Lari Traders and generate a business health report. ")
          .append("You MUST write the report in Hinglish (Hindi written in English/Latin/Roman script, e.g. 'Lari Traders ka business report' instead of Devanagari script). ")
          .append("Do NOT use Devanagari Hindi characters. Keep the report extremely concise, short, and to the point to minimize response latency. Use bullet points.\n\n")
          .append("### METRICS DATA:\n")
          .append("- Current Month net revenue/profit stats are for: Year ").append(year).append(", Month ").append(month).append("\n");

        if (today != null) {
            sb.append("- Today's Revenue: ₹").append(today.getTodayRevenue()).append("\n")
              .append("- Today's Collected Amount: ₹").append(today.getTodayCollected()).append("\n")
              .append("  (Cash: ₹").append(today.getTodayCollectedCash()).append(", UPI: ₹").append(today.getTodayCollectedUpi()).append(", Udhar: ₹").append(today.getTodayCollectedUdhar()).append(")\n")
              .append("- Today's Pending Collection: ₹").append(today.getTodayPending()).append("\n")
              .append("- Today's Total Invoices: ").append(today.getTodayBills()).append("\n")
              .append("- Items in Low Stock: ").append(today.getLowStockCount()).append("\n");
            
            if (today.getLowStockAlerts() != null && !today.getLowStockAlerts().isEmpty()) {
                sb.append("  Low Stock Items: ").append(
                    today.getLowStockAlerts().stream()
                        .map(a -> (a.getProductName() != null ? a.getProductName().replaceAll("[\\[\\]<>{}]", "") : "") + " (current: " + a.getCurrentStock() + ", min: " + a.getThreshold() + ")")
                        .collect(Collectors.joining(", "))
                ).append("\n");
            }

            sb.append("- Expiring Batches: ").append(today.getExpiringBatchesCount()).append("\n");
            if (today.getExpiringBatches() != null && !today.getExpiringBatches().isEmpty()) {
                sb.append("  Expiring Batches Details: ").append(
                    today.getExpiringBatches().stream()
                        .map(b -> (b.getProductName() != null ? b.getProductName().replaceAll("[\\[\\]<>{}]", "") : "") + " (Batch: " + b.getBatchNo() + ", Expiry: " + b.getExpiryDate() + ", stock remaining: " + b.getStockCount() + ")")
                        .collect(Collectors.joining(", "))
                ).append("\n");
            }

            sb.append("- Inactive Customers (no orders for 30+ days): ").append(today.getInactiveCustomersCount()).append("\n");
            if (today.getInactiveCustomers() != null && !today.getInactiveCustomers().isEmpty()) {
                sb.append("  Inactive Shops: ").append(
                    today.getInactiveCustomers().stream()
                        .map(c -> (c.getCustomerName() != null ? c.getCustomerName().replaceAll("[\\[\\]<>{}]", "") : "") + " (" + (c.getShopName() != null ? c.getShopName().replaceAll("[\\[\\]<>{}]", "") : "") + ")")
                        .collect(Collectors.joining(", "))
                ).append("\n");
            }
        }

        if (monthly != null) {
            sb.append("- Monthly Revenue: ₹").append(monthly.getTotalRevenue()).append("\n")
              .append("- Monthly Collected: ₹").append(monthly.getTotalCollected()).append("\n")
              .append("- Monthly Expenses: ₹").append(monthly.getTotalExpenses()).append("\n")
              .append("- Monthly Net Profit: ₹").append(monthly.getNetProfit()).append("\n");
        }

        sb.append("\nGenerate the analysis under these categories:\n")
          .append("1. **Shop Performance Summary (Hinglish)**: Briefly explain today's and this month's status in Roman script.\n")
          .append("2. **Low Stock Alerts & Reorder Plan**: What should be reordered and why.\n")
          .append("3. **Expiry Risks & Clearing Plan**: Recommendations for expiring batches.\n")
          .append("4. **Customer Recovery & Collection**: Tips to recover pending payments and inactive customers.\n");

        return sb.toString();
    }

    private String buildPromptForChat(DashboardSummaryResponse summary, String userMessage) {
        DashboardResponse today = summary.getToday();
        MonthlyReportResponse monthly = summary.getMonthly();

        StringBuilder sb = new StringBuilder();
        sb.append("You are the AI Business Assistant for Lari Traders. Answer the user's question directly, clearly, and concisely in Hinglish (Hindi written in English/Latin/Roman script, e.g. write 'Lari Traders' instead of Devanagari Hindi text). Do NOT use Devanagari script. Keep the response short to minimize latency. Based on these metrics:\n\n")
          .append("### SHOP STATUS:\n");

        if (today != null) {
            sb.append("- Today Revenue: ₹").append(today.getTodayRevenue()).append(", Today Collected: ₹").append(today.getTodayCollected()).append("\n")
              .append("- Today Pending Collection: ₹").append(today.getTodayPending()).append("\n")
              .append("- Low Stock Count: ").append(today.getLowStockCount()).append("\n")
              .append("- Expiring Batches Count: ").append(today.getExpiringBatchesCount()).append("\n")
              .append("- Inactive Customers: ").append(today.getInactiveCustomersCount()).append("\n");
        }
        if (monthly != null) {
            sb.append("- Monthly Revenue: ₹").append(monthly.getTotalRevenue()).append(", Net Profit: ₹").append(monthly.getNetProfit()).append(", Expenses: ₹").append(monthly.getTotalExpenses()).append("\n");
        }

        sb.append("\n### USER QUESTION:\n")
          .append(userMessage)
          .append("\n\nAnswer in a helpful, concise, and short format in Hinglish (Roman script only):");

        return sb.toString();
    }

    private String callPythonTextGen(String prompt) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("prompt", prompt);
            
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(PYTHON_TEXT_GEN_URL, entity, Map.class);
            if (response != null && response.containsKey("text")) {
                return (String) response.get("text");
            }
            return "Unable to parse AI insights text.";
        } catch (Exception e) {
            return "Failed to connect to AI Service: " + e.getMessage();
        }
    }
}
