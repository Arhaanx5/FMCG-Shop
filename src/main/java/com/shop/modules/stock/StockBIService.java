package com.shop.modules.stock;

import com.shop.modules.product.Product;
import com.shop.modules.product.ProductRepository;
import com.shop.modules.billing.Bill;
import com.shop.modules.billing.BillItem;
import com.shop.modules.billing.BillRepository;
import com.shop.modules.billing.BillStatus;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockBIService {

    private final ProductRepository productRepository;
    private final StockBatchRepository batchRepository;
    private final BillRepository billRepository;
    private final StockAdjustmentLogRepository adjustmentLogRepository;
    private final StockReportService reportService;
    private final StockInventoryService inventoryService;

    @Data
    @Builder
    public static class HealthScoreBreakdown {
        private int overallScore;
        private String classification;
        private double deadStockScore;
        private double expiryScore;
        private double lowStockScore;
        private double turnoverScore;
        private double accuracyScore;
        private double deadStockRatio;
        private double expiryRiskRatio;
        private double lowStockRatio;
        private double turnoverRate;
        private double adjustmentRatio;
    }

    public HealthScoreBreakdown calculateHealthScore() {
        List<Product> products = productRepository.findAll();
        List<StockBatch> activeBatches = batchRepository.findAll().stream()
                .filter(b -> b.getSecondaryRemaining() != null && b.getSecondaryRemaining() > 0)
                .collect(Collectors.toList());

        BigDecimal totalVal = BigDecimal.ZERO;
        BigDecimal deadStockVal = BigDecimal.ZERO;
        BigDecimal expiringVal = BigDecimal.ZERO;

        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysLater = today.plusDays(30);

        LocalDateTime ninetyDaysAgo = LocalDateTime.now().minusDays(90);
        List<Bill> bills90Days = billRepository.findBillsBetween(ninetyDaysAgo, LocalDateTime.now());
        Set<UUID> soldProductIds90Days = bills90Days.stream()
                .filter(b -> b.getStatus() != BillStatus.CANCELLED)
                .flatMap(b -> b.getItems().stream())
                .map(item -> item.getProduct().getId())
                .collect(Collectors.toSet());

        for (StockBatch b : activeBatches) {
            Product p = b.getProduct();
            if (p == null) continue;
            int ratio = p.getSecondaryPerPrimary() != null ? p.getSecondaryPerPrimary() : 1;
            BigDecimal costPrice = b.getBuyPricePerSecondary(ratio);
            BigDecimal value = BigDecimal.valueOf(b.getSecondaryRemaining()).multiply(costPrice);
            totalVal = totalVal.add(value);

            if (b.getExpiryDate() != null && b.getExpiryDate().isBefore(thirtyDaysLater)) {
                expiringVal = expiringVal.add(value);
            }

            if (!soldProductIds90Days.contains(p.getId())) {
                deadStockVal = deadStockVal.add(value);
            }
        }

        double deadStockRatio = 0.0;
        if (totalVal.compareTo(BigDecimal.ZERO) > 0) {
            deadStockRatio = deadStockVal.divide(totalVal, 4, RoundingMode.HALF_UP).doubleValue();
        }

        double expiryRiskRatio = 0.0;
        if (totalVal.compareTo(BigDecimal.ZERO) > 0) {
            expiryRiskRatio = expiringVal.divide(totalVal, 4, RoundingMode.HALF_UP).doubleValue();
        }

        long totalSKUs = products.size();
        long lowStockSKUs = 0;
        for (Product p : products) {
            Stock stock = inventoryService.getOrCreateStock(p.getId());
            if (stock.getTotalSecondaryUnits() <= p.getLowStockAlertInSecondary()) {
                lowStockSKUs++;
            }
        }
        double lowStockRatio = totalSKUs > 0 ? (double) lowStockSKUs / totalSKUs : 0.0;

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<Bill> bills30Days = billRepository.findBillsBetween(thirtyDaysAgo, LocalDateTime.now());
        BigDecimal cogs = BigDecimal.ZERO;
        for (Bill bill : bills30Days) {
            if (bill.getStatus() == BillStatus.CANCELLED) continue;
            for (BillItem item : bill.getItems()) {
                BigDecimal unitCost = reportService.calculateWeightedAvgCost(item.getProduct().getId());
                cogs = cogs.add(BigDecimal.valueOf(item.getQuantity() + item.getFreeQuantity()).multiply(unitCost));
            }
        }

        double turnoverRate = 0.0;
        if (totalVal.compareTo(BigDecimal.ZERO) > 0) {
            turnoverRate = cogs.divide(totalVal, 4, RoundingMode.HALF_UP).doubleValue();
        }

        List<StockAdjustmentLog> adjustments = adjustmentLogRepository.findAllByOrderByTimestampDesc().stream()
                .filter(l -> l.getTimestamp().isAfter(thirtyDaysAgo))
                .collect(Collectors.toList());
        long adjustmentsCount = adjustments.size();
        double adjustmentRatio = totalSKUs > 0 ? (double) adjustmentsCount / totalSKUs : 0.0;

        double deadStockScore = (1.0 - deadStockRatio) * 25.0;
        double expiryScore = (1.0 - expiryRiskRatio) * 25.0;
        double lowStockScore = (1.0 - lowStockRatio) * 20.0;
        
        double TARGET_TURNOVER = 0.5;
        double turnoverScore = Math.min(turnoverRate / TARGET_TURNOVER, 1.0) * 20.0;
        
        double accuracyScore = Math.max(0.0, 1.0 - (adjustmentsCount * 0.05)) * 10.0;

        int overall = (int) Math.round(deadStockScore + expiryScore + lowStockScore + turnoverScore + accuracyScore);
        overall = Math.max(0, Math.min(100, overall));

        String classification = "Stable";
        if (overall >= 90) classification = "Excellent";
        else if (overall >= 75) classification = "Good";
        else if (overall >= 60) classification = "Stable";
        else if (overall >= 40) classification = "Warning";
        else classification = "Critical";

        return HealthScoreBreakdown.builder()
                .overallScore(overall)
                .classification(classification)
                .deadStockScore(deadStockScore)
                .expiryScore(expiryScore)
                .lowStockScore(lowStockScore)
                .turnoverScore(turnoverScore)
                .accuracyScore(accuracyScore)
                .deadStockRatio(deadStockRatio)
                .expiryRiskRatio(expiryRiskRatio)
                .lowStockRatio(lowStockRatio)
                .turnoverRate(turnoverRate)
                .adjustmentRatio(adjustmentRatio)
                .build();
    }

    @Data
    @Builder
    public static class ReorderSuggestion {
        private UUID productId;
        private String productName;
        private int currentStock;
        private int lowStockAlert;
        private double avgDailySales;
        private int suggestedReorderQty;
        private String urgency;
    }

    public List<ReorderSuggestion> getReorderSuggestions() {
        List<Product> products = productRepository.findAll();
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<Bill> bills = billRepository.findBillsBetween(thirtyDaysAgo, LocalDateTime.now());

        Map<UUID, Integer> salesMap = new HashMap<>();
        for (Bill b : bills) {
            if (b.getStatus() == BillStatus.CANCELLED) continue;
            for (BillItem item : b.getItems()) {
                salesMap.put(item.getProduct().getId(),
                        salesMap.getOrDefault(item.getProduct().getId(), 0) + item.getQuantity());
            }
        }

        List<ReorderSuggestion> suggestions = new ArrayList<>();
        for (Product p : products) {
            Stock stock = inventoryService.getOrCreateStock(p.getId());
            int currentStock = stock.getTotalSecondaryUnits();
            int lowStockAlert = p.getLowStockAlertInSecondary();

            if (currentStock <= lowStockAlert) {
                int totalSales = salesMap.getOrDefault(p.getId(), 0);
                double avgDaily = totalSales / 30.0;
                
                int ratio = p.getSecondaryPerPrimary() != null ? p.getSecondaryPerPrimary() : 1;
                int minQty = ratio * 2;
                int suggested = (int) Math.round(avgDaily * 30);
                suggested = Math.max(minQty, suggested);

                String urgency = "NORMAL";
                if (currentStock == 0) {
                    urgency = "CRITICAL";
                } else if (currentStock <= lowStockAlert / 2) {
                    urgency = "WARNING";
                }

                suggestions.add(ReorderSuggestion.builder()
                        .productId(p.getId())
                        .productName(p.getName())
                        .currentStock(currentStock)
                        .lowStockAlert(lowStockAlert)
                        .avgDailySales(avgDaily)
                        .suggestedReorderQty(suggested)
                        .urgency(urgency)
                        .build());
            }
        }
        
        suggestions.sort((a, b) -> b.getUrgency().compareTo(a.getUrgency()));
        return suggestions;
    }
}
