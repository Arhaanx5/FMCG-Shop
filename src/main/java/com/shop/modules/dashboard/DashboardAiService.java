package com.shop.modules.dashboard;

import com.shop.modules.billing.Bill;
import com.shop.modules.billing.BillItem;
import com.shop.modules.billing.BillRepository;
import com.shop.modules.billing.BillStatus;
import com.shop.modules.expense.Expense;
import com.shop.modules.expense.ExpenseRepository;
import com.shop.modules.expense.ExpenseCategory;
import com.shop.modules.khata.Payment;
import com.shop.modules.khata.PaymentRepository;
import com.shop.modules.product.ProductRepository;
import com.shop.modules.stock.StockBatchRepository;
import com.shop.modules.customer.CustomerRepository;
import com.shop.modules.dashboard.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardAiService {

    private final DashboardService dashboardService;
    private final HealthReportRepository healthReportRepository;
    private final BillRepository billRepository;
    private final ExpenseRepository expenseRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final StockBatchRepository batchRepository;
    private final CustomerRepository customerRepository;
    private final ObjectMapper objectMapper;
    private final com.shop.modules.receivables.ReceivablesAgingService receivablesAgingService;

    private static final String PYTHON_TEXT_GEN_URL = "http://127.0.0.1:8087/ocr/generate-text";
    private static final String PYTHON_STRUCTURED_URL = "http://127.0.0.1:8087/ocr/parse-structured";
    private final Map<String, String> insightsCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final String[] MONTH_NAMES = {"", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    // ── Existing Insights Method ──
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

    // ── Existing Chat Method ──
    public String chatWithDashboard(String message, int year, int month) {
        String sanitizedMessage = message.replaceAll("[\\[\\]<>{}]", "").trim();
        if (sanitizedMessage.length() > 500) {
            sanitizedMessage = sanitizedMessage.substring(0, 500);
        }
        DashboardSummaryResponse summary = dashboardService.getDashboardSummary(year, month);
        String prompt = buildPromptForChat(summary, sanitizedMessage);
        return callPythonTextGen(prompt);
    }

    // ── CFO Health Report Method ──
    public DashboardHealthReportResponse generateHealthReport(int year, int month, boolean forceRefresh) {
        LocalDate current = LocalDate.now();
        int currentYear = current.getYear();
        int currentMonth = current.getMonthValue();
        boolean isPastMonth = (year < currentYear) || (year == currentYear && month < currentMonth);

        // 1. Check DB Cache First (Past months permanent, current month 5 mins TTL)
        Optional<HealthReport> cachedReportOpt = healthReportRepository.findByReportYearAndReportMonth(year, month);
        if (!forceRefresh && cachedReportOpt.isPresent()) {
            HealthReport cached = cachedReportOpt.get();
            if (isPastMonth) {
                log.info("Returning cached CFO health report for closed month: {}-{}", year, month);
                return deserializeReport(cached.getReportJson());
            } else {
                // Current month - check 5-minute TTL
                if (cached.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(5))) {
                    log.info("Returning cached CFO health report for active month: {}-{} (within 5-min TTL)", year, month);
                    return deserializeReport(cached.getReportJson());
                }
            }
        }

        // 2. Concurrency Guard using double-checked lock on JVM-interned cache key
        String lockKey = (year + "-" + month).intern();
        synchronized (lockKey) {
            // Double-check DB cache inside lock
            Optional<HealthReport> doubleCheckOpt = healthReportRepository.findByReportYearAndReportMonth(year, month);
            if (!forceRefresh && doubleCheckOpt.isPresent()) {
                HealthReport cached = doubleCheckOpt.get();
                if (isPastMonth || cached.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(5))) {
                    return deserializeReport(cached.getReportJson());
                }
            }

            // 3. Early Return/Empty Data Guard
            LocalDateTime monthStart = LocalDate.of(year, month, 1).atStartOfDay();
            LocalDateTime monthEnd = monthStart.plusMonths(1).minusNanos(1000);

            List<Bill> monthBills = billRepository.findBillsBetween(monthStart, monthEnd);
            List<Expense> expenses = expenseRepository.findBetween(LocalDate.of(year, month, 1), LocalDate.of(year, month, 1).plusMonths(1).minusDays(1));

            if (monthBills.isEmpty() && expenses.isEmpty()) {
                log.info("No bills or expenses logged for {}-{} | Returning early with NO_DATA status", year, month);
                return DashboardHealthReportResponse.builder()
                        .overallScore(null)
                        .status("NO_DATA")
                        .healthExplanation("Lari Traders ka business data is mahine ke liye available nahi hai ya bills aur expenses empty hain.")
                        .build();
            }

            // 4. Data Aggregation & KPI Formulations
            MonthlyReportResponse monthlyReport = dashboardService.getMonthlyReport(year, month);
            
            // Query current low stock and expiring items
            long lowStockCount = productRepository.findLowStockProducts().size();
            long expiringCount = batchRepository.findExpiringBefore(LocalDate.now().plusDays(30)).size();
            long inactiveCount = customerRepository.findInactiveCustomers(LocalDateTime.now().minusDays(15)).size();
            
            // Check Data availability status flags
            boolean isBillsAvailable = !monthBills.isEmpty();
            boolean isExpensesAvailable = !expenses.isEmpty();
            boolean isInventoryAvailable = batchRepository.existsByExhaustedFalse();
            boolean isCustomersAvailable = customerRepository.count() > 0;

            // Receivables aging (0-30, 31-60, 61-90, 90+ days from createdAt)
            List<Bill> pendingBills = billRepository.findPendingBills();
            com.shop.modules.receivables.dto.ReceivablesAgingResult aging = receivablesAgingService.calculateAging(pendingBills);
            BigDecimal age30 = aging.getAge30();
            BigDecimal age60 = aging.getAge60();
            BigDecimal age90 = aging.getAge90();
            BigDecimal age90Plus = aging.getAge90Plus();

            // Calculate COGS Fallback & Confidence Details
            int totalBillItems = 0;
            int unlinkedBillItems = 0;
            for (Bill bill : monthBills) {
                if (bill.getStatus() == BillStatus.CANCELLED) continue;
                for (BillItem item : bill.getItems()) {
                    totalBillItems++;
                    if (item.getBatch() == null) {
                        unlinkedBillItems++;
                    }
                }
            }
            double fallbackPercentage = 0.0;
            if (totalBillItems > 0) {
                fallbackPercentage = (unlinkedBillItems * 100.0) / totalBillItems;
            }

            // 5. Build prompt mapping the structured 17 KPIs
            String prompt = buildPromptForHealthReport(
                    monthlyReport, year, month, fallbackPercentage,
                    age30, age60, age90, age90Plus,
                    lowStockCount, expiringCount, inactiveCount,
                    isBillsAvailable, isExpensesAvailable, isInventoryAvailable, isCustomersAvailable
            );

            String systemInstruction = "You are a professional CFO and Financial Analyst. Analyze the provided metrics and output a Business Health Report. "
                    + "Your output MUST strictly be a valid JSON object matching the requested schema. No markdown formatting outside of JSON, no introductory words. "
                    + "Write explanations and task instructions in Hinglish (Hindi written in Roman script, e.g. 'Business stable hai but margin improvement chahiye'). "
                    + "Scores, metrics, and KPI ratios MUST be returned accurately. Optional score fields can be set to null if modules show 'Not Available'.";

            // 6. Call Python API with Connection/Read Timeout & Retry-Once Resiliency
            Map<String, Object> responseMap = callStructuredOcrWithRetry(prompt, systemInstruction);
            DashboardHealthReportResponse reportDto = parseAndValidate(responseMap);

            // Attach raw metrics from backend calculations
            DashboardHealthReportResponse.RawMetrics raw = DashboardHealthReportResponse.RawMetrics.builder()
                    .revenue(monthlyReport.getTotalRevenue())
                    .netProfit(monthlyReport.getNetProfit())
                    .totalExpenses(monthlyReport.getTotalExpenses())
                    .build();
            reportDto.setRawMetrics(raw);


            // Programmatic safety cap on profitability score & overall score if database batch linking is highly incomplete (>50% unlinked items)
            if (fallbackPercentage > 50.0) {
                if (reportDto.getProfitabilityDetails() != null && reportDto.getProfitabilityDetails().getScore() != null) {
                    if (reportDto.getProfitabilityDetails().getScore() > 70) {
                        log.info("Programmatic Profitability Score cap applied: reduced from {} to 70 due to fallback percentage {}%", reportDto.getProfitabilityDetails().getScore(), fallbackPercentage);
                        reportDto.getProfitabilityDetails().setScore(70);
                    }
                }
                if (reportDto.getOverallScore() != null && reportDto.getOverallScore() > 80) {
                    log.info("Programmatic Overall Score cap applied: reduced from {} to 80 due to fallback percentage {}%", reportDto.getOverallScore(), fallbackPercentage);
                    reportDto.setOverallScore(80);
                }
            }

            // 7. Persist to DB cache (with multi-instance unique constraint collision guard)
            try {
                String reportJson = objectMapper.writeValueAsString(reportDto);
                HealthReport reportEntity = doubleCheckOpt.orElse(new HealthReport());
                reportEntity.setReportYear(year);
                reportEntity.setReportMonth(month);
                reportEntity.setReportJson(reportJson);
                reportEntity.setCreatedAt(LocalDateTime.now());
                healthReportRepository.saveAndFlush(reportEntity);
            } catch (org.springframework.dao.DataIntegrityViolationException dive) {
                log.warn("Multi-instance race condition hit: UNIQUE constraint triggered for {}-{}. Fetching existing report.", year, month, dive);
                return healthReportRepository.findByReportYearAndReportMonth(year, month)
                        .map(r -> deserializeReport(r.getReportJson()))
                        .orElseThrow(() -> new HealthReportException("DB unique constraint violation and subsequent lookup failed.", dive));
            } catch (Exception e) {
                log.error("Failed to serialize or save CFO health report to database: ", e);
            }

            return reportDto;
        }
    }

    private String buildPromptForHealthReport(
            MonthlyReportResponse monthly, int year, int month, double fallbackPercentage,
            BigDecimal age30, BigDecimal age60, BigDecimal age90, BigDecimal age90Plus,
            long lowStockCount, long expiringCount, long inactiveCount,
            boolean isBillsAvailable, boolean isExpensesAvailable, boolean isInventoryAvailable, boolean isCustomersAvailable) {

        StringBuilder sb = new StringBuilder();
        sb.append("Please analyze the business metrics below for Lari Traders for Year ").append(year).append(", Month ").append(month).append(".\n\n")
          .append("### DATA AVAILABILITY FLAGS:\n")
          .append("- Invoices/Revenue Data: ").append(isBillsAvailable ? "Available" : "Not Available").append("\n")
          .append("- Expense Data: ").append(isExpensesAvailable ? "Available" : "Not Available").append("\n")
          .append("- Inventory Catalog: ").append(isInventoryAvailable ? "Available" : "Not Available").append("\n")
          .append("- Customer Profiles: ").append(isCustomersAvailable ? "Available" : "Not Available").append("\n\n")

          .append("### FINANCIAL SUMMARY:\n")
          .append("- Total Monthly Invoices: ").append(monthly.getTotalBills()).append("\n")
          .append("- Monthly Revenue: INR ").append(monthly.getTotalRevenue()).append("\n")
          .append("- Monthly Collected: INR ").append(monthly.getTotalCollected())
          .append(" (Cash: INR ").append(monthly.getTotalCollectedCash()).append(", UPI: INR ").append(monthly.getTotalCollectedUpi()).append(", Udhar Collected: INR ").append(monthly.getTotalCollectedUdhar()).append(")\n")
          .append("- Total Monthly Expenses: INR ").append(monthly.getTotalExpenses()).append("\n")
          .append("  (Expense Breakdown by Category: ").append(monthly.getExpensesByCategory()).append(")\n")
          .append("- Net Profit: INR ").append(monthly.getNetProfit()).append("\n")
          .append("- Stock Waste / Damage Loss: INR ").append(monthly.getTotalDamageLoss()).append("\n")
          .append("- Top Sold Products: ").append(monthly.getTopProductsByQty()).append("\n\n")

          .append("### INVENTORY METRICS:\n")
          .append("- Low Stock Alerts: ").append(lowStockCount).append(" items\n")
          .append("- Expiring Batches in next 30 days: ").append(expiringCount).append(" batches\n\n")

          .append("### CUSTOMER METRICS:\n")
          .append("- Inactive Customers (no order for 15+ days): ").append(inactiveCount).append("\n\n")

          .append("### ACCOUNTS RECEIVABLES (UDHAR) AGING SUMMARY:\n")
          .append("- Total Pending Month Collection: INR ").append(monthly.getTotalPending()).append("\n")
          .append("- Aging ranges calculated from invoice creation date:\n")
          .append("  * 0-30 Days Overdue: INR ").append(age30).append("\n")
          .append("  * 31-60 Days Overdue: INR ").append(age60).append("\n")
          .append("  * 61-90 Days Overdue: INR ").append(age90).append("\n")
          .append("  * 90+ Days Overdue: INR ").append(age90Plus).append("\n\n");

        if (fallbackPercentage > 30.0) {
            sb.append("### CRITICAL DATA RELIABILITY WARNING:\n")
              .append("- COGS batch-fallback disclosure: ").append(String.format("%.2f", fallbackPercentage))
              .append("% of COGS used current product catalog default pricing due to missing batch records.\n")
              .append("WARNING: Profitability figures carry significant estimation uncertainty due to incomplete batch-cost data. ")
              .append("Treat profit margin scores conservatively and decrease the profitability health score accordingly.\n\n");
        } else if (fallbackPercentage > 0.0) {
            sb.append("### DATA RELIABILITY NOTE:\n")
              .append("- COGS batch-fallback disclosure: ").append(String.format("%.2f", fallbackPercentage))
              .append("% of COGS used current product catalog default pricing. Minor margin estimation variance is possible.\n\n");
        }

        sb.append("Generate the report using the following JSON schema format. Return ONLY the JSON object:\n")
          .append("{\n")
          .append("  \"overallScore\": <Integer 0-100 representing overall health>,\n")
          .append("  \"status\": \"<HEALTHY | STABLE | DECLINING | AT_RISK>\",\n")
          .append("  \"healthExplanation\": \"<Hinglish summary analysis of the month's health>\",\n")
          .append("  \"profitabilityDetails\": {\n")
          .append("     \"score\": <Integer or null>,\n")
          .append("     \"rating\": \"<Good | Average | Critical | N/A>\",\n")
          .append("     \"explanation\": \"<Hinglish profitability diagnosis details>\",\n")
          .append("     \"kpis\": {\"Revenue\": \"INR XXX\", \"Net Profit\": \"INR XXX\", \"COGS Fallback\": \"X%\"},\n")
          .append("     \"diagnoses\": [\"Bullet point 1 in Hinglish\", \"Bullet point 2 in Hinglish\"]\n")
          .append("  },\n")
          .append("  \"cashFlowDetails\": {\n")
          .append("     \"score\": <Integer or null>,\n")
          .append("     \"rating\": \"<Good | Average | Critical | N/A>\",\n")
          .append("     \"explanation\": \"<Hinglish cash flow diagnosis details>\",\n")
          .append("     \"kpis\": {\"Cash vs UPI Ratio\": \"XX%\", \"Total Collected\": \"INR XXX\"},\n")
          .append("     \"diagnoses\": []\n")
          .append("  },\n")
          .append("  \"inventoryDetails\": {\n")
          .append("     \"score\": <Integer or null>,\n")
          .append("     \"rating\": \"<Good | Average | Critical | N/A>\",\n")
          .append("     \"explanation\": \"<Hinglish inventory efficiency details>\",\n")
          .append("     \"kpis\": {\"Low Stock Count\": \"XX\", \"Expiry Risks\": \"XX\"},\n")
          .append("     \"diagnoses\": []\n")
          .append("  },\n")
          .append("  \"customerDetails\": {\n")
          .append("     \"score\": <Integer or null>,\n")
          .append("     \"rating\": \"<Good | Average | Critical | N/A>\",\n")
          .append("     \"explanation\": \"<Hinglish customer metric diagnostics>\",\n")
          .append("     \"kpis\": {\"Inactive Customers\": \"XX\"},\n")
          .append("     \"diagnoses\": []\n")
          .append("  },\n")
          .append("  \"receivablesDetails\": {\n")
          .append("     \"score\": <Integer or null>,\n")
          .append("     \"rating\": \"<Good | Average | Critical | N/A>\",\n")
          .append("     \"explanation\": \"<Hinglish receivables aging diagnostics>\",\n")
          .append("     \"kpis\": {\"0-30 Days Overdue\": \"INR XXX\", \"90+ Days Overdue\": \"INR XXX\"},\n")
          .append("     \"diagnoses\": []\n")
          .append("  },\n")
          .append("  \"suppliersDetails\": {\n")
          .append("     \"score\": <Integer or null>,\n")
          .append("     \"rating\": \"<Good | Average | Critical | N/A>\",\n")
          .append("     \"explanation\": \"<Hinglish vendor expenses summary>\",\n")
          .append("     \"kpis\": {\"Stock Purchases\": \"INR XXX\"},\n")
          .append("     \"diagnoses\": []\n")
          .append("  },\n")
          .append("  \"operationalDetails\": {\n")
          .append("     \"score\": <Integer or null>,\n")
          .append("     \"rating\": \"<Good | Average | Critical | N/A>\",\n")
          .append("     \"explanation\": \"<Hinglish delivery/performance metrics summary>\",\n")
          .append("     \"kpis\": {},\n")
          .append("     \"diagnoses\": []\n")
          .append("  },\n")
          .append("  \"actionChecklist\": [\n")
          .append("     {\n")
          .append("       \"task\": \"<Title of action item>\",\n")
          .append("       \"category\": \"<INVENTORY | RECEIVABLES | CASH_FLOW | EXPENSES | CUSTOMER>\",\n")
          .append("       \"urgency\": \"<HIGH | MEDIUM | LOW>\",\n")
          .append("       \"instructions\": \"<Step-by-step description of how to execute in Hinglish>\"\n")
          .append("     }\n")
          .append("  ]\n")
          .append("}");

        return sb.toString();
    }

    private Map<String, Object> callStructuredOcrWithRetry(String prompt, String systemInstruction) {
        int maxAttempts = 2;
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // Set 45-second Connection/Read Timeout on RestTemplate
                SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                factory.setConnectTimeout(45000);
                factory.setReadTimeout(45000);
                RestTemplate restTemplate = new RestTemplate(factory);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, String> body = new HashMap<>();
                body.put("prompt", prompt);
                body.put("systemInstruction", systemInstruction);

                HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
                
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(PYTHON_STRUCTURED_URL, entity, Map.class);
                if (response != null) {
                    return response;
                }
                throw new RuntimeException("Empty response body from python OCR service.");
            } catch (Exception ex) {
                lastException = ex;
                log.warn("Attempt {} failed for structured health report call: {}", attempt, ex.getMessage());
                if (attempt < maxAttempts) {
                    try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
        throw new HealthReportException("CFO AI microservice is currently unreachable or timed out. Please try again.", lastException);
    }

    private DashboardHealthReportResponse parseAndValidate(Map<String, Object> responseMap) {
        try {
            // Jackson configuration (ignore unknown fields, allow safe mappings)
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);

            DashboardHealthReportResponse report = mapper.convertValue(responseMap, DashboardHealthReportResponse.class);

            if (report == null) {
                throw new HealthReportException("Deserialization returned null DTO");
            }
            if (report.getOverallScore() == null) {
                throw new HealthReportException("Overall Score is missing in the structured response");
            }
            if (report.getStatus() == null) {
                throw new HealthReportException("Business Health Status is missing in the structured response");
            }
            if (report.getHealthExplanation() == null) {
                throw new HealthReportException("Health Summary Explanation is missing in the structured response");
            }

            // Guard against null categories
            if (report.getProfitabilityDetails() == null) report.setProfitabilityDetails(new DashboardHealthReportResponse.CategoryDetails());
            if (report.getCashFlowDetails() == null) report.setCashFlowDetails(new DashboardHealthReportResponse.CategoryDetails());
            if (report.getInventoryDetails() == null) report.setInventoryDetails(new DashboardHealthReportResponse.CategoryDetails());
            if (report.getCustomerDetails() == null) report.setCustomerDetails(new DashboardHealthReportResponse.CategoryDetails());
            if (report.getReceivablesDetails() == null) report.setReceivablesDetails(new DashboardHealthReportResponse.CategoryDetails());
            if (report.getSuppliersDetails() == null) report.setSuppliersDetails(new DashboardHealthReportResponse.CategoryDetails());
            if (report.getOperationalDetails() == null) report.setOperationalDetails(new DashboardHealthReportResponse.CategoryDetails());
            if (report.getActionChecklist() == null) report.setActionChecklist(new ArrayList<>());

            return report;
        } catch (Exception e) {
            throw new HealthReportException("Structured report mapping validation failed: " + e.getMessage(), e);
        }
    }

    private DashboardHealthReportResponse deserializeReport(String reportJson) {
        try {
            return objectMapper.readValue(reportJson, DashboardHealthReportResponse.class);
        } catch (Exception e) {
            throw new HealthReportException("Failed to deserialize health report from cache: " + e.getMessage(), e);
        }
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

    public TrendSummaryResponse getTrendData(int limit) {
        if (limit <= 0) {
            limit = 12;
        }

        // Fetch reports sorted descending by year and month using pageable limit
        List<HealthReport> reports = healthReportRepository.findAllByOrderByReportYearDescReportMonthDesc(PageRequest.of(0, limit));

        List<HealthReportTrendResponse> trendList = new ArrayList<>();

        for (HealthReport entity : reports) {
            DashboardHealthReportResponse reportDto = deserializeReport(entity.getReportJson());
            if (reportDto == null) continue;

            int year = entity.getReportYear();
            int month = entity.getReportMonth();

            // Lazy backfill/auto-persist if rawMetrics is null
            if (reportDto.getRawMetrics() == null) {
                log.info("Executing lazy backfill calculation for health report: {}-{}", year, month);
                try {
                    MonthlyReportResponse monthly = dashboardService.getMonthlyReport(year, month);
                    DashboardHealthReportResponse.RawMetrics raw = DashboardHealthReportResponse.RawMetrics.builder()
                            .revenue(monthly.getTotalRevenue() != null ? monthly.getTotalRevenue() : BigDecimal.ZERO)
                            .netProfit(monthly.getNetProfit() != null ? monthly.getNetProfit() : BigDecimal.ZERO)
                            .totalExpenses(monthly.getTotalExpenses() != null ? monthly.getTotalExpenses() : BigDecimal.ZERO)
                            .build();
                    reportDto.setRawMetrics(raw);

                    // Update entity and save back to database
                    entity.setReportJson(objectMapper.writeValueAsString(reportDto));
                    healthReportRepository.save(entity);
                    log.info("Lazy backfill successfully saved to DB for report: {}-{}", year, month);
                } catch (Exception e) {
                    log.error("Failed to execute or save lazy backfill for report {}-{}: ", year, month, e);
                }
            }

            DashboardHealthReportResponse.RawMetrics raw = reportDto.getRawMetrics();
            BigDecimal revenue = raw != null ? raw.getRevenue() : BigDecimal.ZERO;
            BigDecimal netProfit = raw != null ? raw.getNetProfit() : BigDecimal.ZERO;
            BigDecimal totalExpenses = raw != null ? raw.getTotalExpenses() : BigDecimal.ZERO;

            String monthName = (month >= 1 && month <= 12) ? MONTH_NAMES[month] : String.valueOf(month);

            HealthReportTrendResponse trend = HealthReportTrendResponse.builder()
                    .year(year)
                    .month(month)
                    .monthName(monthName)
                    .overallScore(reportDto.getOverallScore())
                    .profitabilityScore(reportDto.getProfitabilityDetails() != null ? reportDto.getProfitabilityDetails().getScore() : null)
                    .cashFlowScore(reportDto.getCashFlowDetails() != null ? reportDto.getCashFlowDetails().getScore() : null)
                    .inventoryScore(reportDto.getInventoryDetails() != null ? reportDto.getInventoryDetails().getScore() : null)
                    .customerScore(reportDto.getCustomerDetails() != null ? reportDto.getCustomerDetails().getScore() : null)
                    .receivablesScore(reportDto.getReceivablesDetails() != null ? reportDto.getReceivablesDetails().getScore() : null)
                    .suppliersScore(reportDto.getSuppliersDetails() != null ? reportDto.getSuppliersDetails().getScore() : null)
                    .operationalScore(reportDto.getOperationalDetails() != null ? reportDto.getOperationalDetails().getScore() : null)
                    .revenue(revenue)
                    .netProfit(netProfit)
                    .totalExpenses(totalExpenses)
                    .status(reportDto.getStatus())
                    .build();

            trendList.add(trend);
        }

        // Reverse to make it chronological (oldest to newest)
        Collections.reverse(trendList);

        // MoM calculations (between the last two elements if we have at least 2)
        Integer currentScore = null;
        Integer prevScore = null;
        Integer delta = null;
        String deltaExplanation = "Pichle mahine ke trends dekhne ke liye report data available nahi hai.";

        if (!trendList.isEmpty()) {
            HealthReportTrendResponse latestTrend = trendList.get(trendList.size() - 1);
            currentScore = latestTrend.getOverallScore();

            if (trendList.size() >= 2) {
                HealthReportTrendResponse prevTrend = trendList.get(trendList.size() - 2);
                prevScore = prevTrend.getOverallScore();

                if (currentScore != null && prevScore != null) {
                    delta = currentScore - prevScore;
                    if (delta > 0) {
                        deltaExplanation = String.format("Is mahine ka Business Health Score %d hai, jo pichle mahine se %d points behtar hai! 📈", currentScore, delta);
                    } else if (delta < 0) {
                        deltaExplanation = String.format("Is mahine ka Business Health Score %d hai, jo pichle mahine se %d points kam hai. 📉 Margin ya inventory control behtar kijiye.", currentScore, Math.abs(delta));
                    } else {
                        deltaExplanation = String.format("Is mahine ka Business Health Score %d hai, jo pichle mahine ke barabar (stable) hai. ⚖️", currentScore);
                    }
                } else if (currentScore == null) {
                    deltaExplanation = "Is mahine ka data empty hai (NO_DATA).";
                } else {
                    deltaExplanation = String.format("Is mahine ka score %d hai, par pichle mahine ka data available nahi tha.", currentScore);
                }
            } else if (currentScore != null) {
                deltaExplanation = String.format("Is mahine ka Business Health Score %d hai. Trend analysis ke liye agle mahine data log kijiye.", currentScore);
            }
        }

        return TrendSummaryResponse.builder()
                .trends(trendList)
                .currentMonthScore(currentScore)
                .previousMonthScore(prevScore)
                .scoreDelta(delta)
                .deltaExplanation(deltaExplanation)
                .build();
    }
}
