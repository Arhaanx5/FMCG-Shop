package com.shop.modules.stock;

import com.shop.modules.product.Product;
import com.shop.modules.product.ProductRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockDashboardService {

    private final ProductRepository productRepository;
    private final StockBatchRepository batchRepository;
    private final StockRepository stockRepository;
    private final StockBIService biService;
    private final StockMovementRepository movementRepository;

    @Data
    @Builder
    public static class DashboardSummary {
        private BigDecimal totalCostValue;
        private BigDecimal totalCostValueWithTax;
        private BigDecimal totalMrpValue;
        private BigDecimal expectedProfit;
        private long totalProducts;
        private long activeSkus;
        private long totalBatches;
        private long totalStockQty;
        private long lowStockCount;
        private long outOfStockCount;
        private long expiringCount;
        private long expiredCount;
        private BigDecimal deadStockValue;
        private int healthScore;
        private String healthClassification;
        private List<RecentBatchDTO> recentBatches;
        // Bug #3: count of products with no sell price — causes MRP understatement
        private long skusWithMissingPrice;
    }

    @Data
    @Builder
    public static class RecentBatchDTO {
        private String id;
        private String productName;
        private String batchNumber;
        private String supplierName;
        private Integer secondaryReceived;
        private String receivedAt;
    }

    @Data
    @Builder
    public static class MonthlyFlowDTO {
        private String month;          // e.g. "Jun 2025"
        private BigDecimal stockAddedValue;   // total purchase value (incl. GST) that month
        private BigDecimal stockSoldValue;    // total sale value that month (qty × sellPrice)
        private BigDecimal netChange;         // added - sold
    }

    public DashboardSummary getDashboardSummary() {
        List<Product> products = productRepository.findAll();

        // Bug #2 Fix: Load all batches ONCE — reuse for both active filtering and recent DTOs
        List<StockBatch> allBatches = batchRepository.findAll();

        List<StockBatch> activeBatches = allBatches.stream()
                .filter(b -> b.getSecondaryRemaining() != null && b.getSecondaryRemaining() > 0)
                .collect(Collectors.toList());

        BigDecimal costValue = BigDecimal.ZERO;
        BigDecimal costValueWithTax = BigDecimal.ZERO;
        BigDecimal mrpValue = BigDecimal.ZERO;
        long totalStockQty = 0;
        long expiringCount = 0;
        long expiredCount = 0;
        BigDecimal deadStockValue = BigDecimal.ZERO;

        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysLater = today.plusDays(30);

        // Bug #2 Fix: reuse allBatches (no second DB call)
        List<RecentBatchDTO> recentDtos = allBatches.stream()
                .sorted((a, b) -> b.getReceivedAt().compareTo(a.getReceivedAt()))
                .limit(10)
                .map(b -> RecentBatchDTO.builder()
                        .id(b.getId() != null ? b.getId().toString() : null)
                        .productName(b.getProduct() != null ? b.getProduct().getName() : "Unknown Product")
                        .batchNumber(b.getBatchNumber())
                        .supplierName(b.getSupplierName())
                        .secondaryReceived(b.getSecondaryReceived())
                        .receivedAt(b.getReceivedAt() != null ? b.getReceivedAt().toString() : null)
                        .build())
                .collect(Collectors.toList());

        StockBIService.HealthScoreBreakdown score = biService.calculateHealthScore();

        for (StockBatch b : activeBatches) {
            Product p = b.getProduct();
            if (p == null) continue;
            int ratio = p.getSecondaryPerPrimary() != null ? p.getSecondaryPerPrimary() : 1;
            BigDecimal costPrice = b.getBuyPricePerSecondary(ratio);
            BigDecimal sellPrice = p.getSellPriceSecondary() != null ? p.getSellPriceSecondary() : BigDecimal.ZERO;

            int remaining = b.getSecondaryRemaining();
            totalStockQty += remaining;

            costValue = costValue.add(BigDecimal.valueOf(remaining).multiply(costPrice));

            // Incl. GST cost per secondary unit
            BigDecimal buyPriceWithTaxPerSec = (b.getBuyPriceWithTax() != null)
                    ? b.getBuyPriceWithTax().divide(BigDecimal.valueOf(ratio), 4, RoundingMode.HALF_UP)
                    : costPrice.multiply(BigDecimal.ONE.add(
                        (b.getGstPercent() != null ? b.getGstPercent() : BigDecimal.ZERO)
                        .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)));
            costValueWithTax = costValueWithTax.add(BigDecimal.valueOf(remaining).multiply(buyPriceWithTaxPerSec));

            mrpValue = mrpValue.add(BigDecimal.valueOf(remaining).multiply(sellPrice));

            if (b.getExpiryDate() != null) {
                if (b.getExpiryDate().isBefore(today)) {
                    expiredCount++;
                } else if (b.getExpiryDate().isBefore(thirtyDaysLater)) {
                    expiringCount++;
                }
            }
        }

        deadStockValue = totalValFromRatio(score.getDeadStockRatio(), costValue);

        long activeSkus = 0;
        long lowStockCount = 0;
        long outOfStockCount = 0;
        long skusWithMissingPrice = 0; // Bug #3: track products missing sell price

        for (Product p : products) {
            Stock stock = stockRepository.findByProductId(p.getId()).orElse(null);
            int qty = stock != null ? stock.getTotalSecondaryUnits() : 0;
            if (qty > 0) {
                activeSkus++;
            } else {
                outOfStockCount++;
            }
            if (qty > 0 && qty <= p.getLowStockAlertInSecondary()) {
                lowStockCount++;
            }
            // Bug #3: count products with no sell price (MRP would be understated)
            if (p.getSellPriceSecondary() == null || p.getSellPriceSecondary().compareTo(BigDecimal.ZERO) == 0) {
                skusWithMissingPrice++;
            }
        }

        // Bug #1 Fix: Profit = MRP − costWithTax (consistent with displayed "Inventory Cost" which is incl. GST)
        BigDecimal expectedProfit = mrpValue.subtract(costValueWithTax);

        return DashboardSummary.builder()
                .totalCostValue(costValue.setScale(2, RoundingMode.HALF_UP))
                .totalCostValueWithTax(costValueWithTax.setScale(2, RoundingMode.HALF_UP))
                .totalMrpValue(mrpValue.setScale(2, RoundingMode.HALF_UP))
                .expectedProfit(expectedProfit.setScale(2, RoundingMode.HALF_UP))
                .totalProducts(products.size())
                .activeSkus(activeSkus)
                .totalBatches(activeBatches.size())
                .totalStockQty(totalStockQty)
                .lowStockCount(lowStockCount)
                .outOfStockCount(outOfStockCount)
                .expiringCount(expiringCount)
                .expiredCount(expiredCount)
                .deadStockValue(deadStockValue.setScale(2, RoundingMode.HALF_UP))
                .healthScore(score.getOverallScore())
                .healthClassification(score.getClassification())
                .recentBatches(recentDtos)
                .skusWithMissingPrice(skusWithMissingPrice)
                .build();
    }

    /**
     * Monthly Inventory Flow Chart data.
     * Stock Added = sum of (primaryReceived × buyPriceWithTax) per batch received that month.
     * Stock Sold  = sum of (|quantity| × sellPriceSecondary) per SALE movement that month.
     * Performance: Both queries are time-bounded (last N months) — no full table scans.
     *
     * @param months Number of past months to include (max 12)
     */
    public List<MonthlyFlowDTO> getMonthlyInventoryFlow(int months) {
        int safeMonths = Math.min(Math.max(months, 1), 12);
        LocalDateTime since = LocalDateTime.now().minusMonths(safeMonths).withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0);
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("MMM yyyy");

        // Build month buckets (ordered oldest → newest)
        List<YearMonth> buckets = new ArrayList<>();
        YearMonth start = YearMonth.now().minusMonths(safeMonths - 1);
        for (int i = 0; i < safeMonths; i++) {
            buckets.add(start.plusMonths(i));
        }

        // Stock Added: from StockBatch.receivedAt — no extra DB call beyond time window
        Map<YearMonth, BigDecimal> addedByMonth = new LinkedHashMap<>();
        for (YearMonth ym : buckets) addedByMonth.put(ym, BigDecimal.ZERO);

        List<StockBatch> recentBatches = batchRepository.findAll().stream()
                .filter(b -> b.getReceivedAt() != null && !b.getReceivedAt().isBefore(since))
                .collect(Collectors.toList());

        for (StockBatch b : recentBatches) {
            YearMonth ym = YearMonth.from(b.getReceivedAt());
            if (addedByMonth.containsKey(ym)) {
                int primary = b.getPrimaryReceived() != null ? b.getPrimaryReceived() : 0;
                BigDecimal price = b.getBuyPriceWithTax() != null ? b.getBuyPriceWithTax() : BigDecimal.ZERO;
                addedByMonth.merge(ym, BigDecimal.valueOf(primary).multiply(price), BigDecimal::add);
            }
        }

        // Stock Sold: from SALE movements — narrow query (only SALE type + time-bounded)
        Map<YearMonth, BigDecimal> soldByMonth = new LinkedHashMap<>();
        for (YearMonth ym : buckets) soldByMonth.put(ym, BigDecimal.ZERO);

        List<StockMovement> saleMovements = movementRepository.findSaleMovementsSince(since);
        for (StockMovement m : saleMovements) {
            YearMonth ym = YearMonth.from(m.getTimestamp());
            if (soldByMonth.containsKey(ym)) {
                Product p = m.getProduct();
                BigDecimal sellPrice = (p != null && p.getSellPriceSecondary() != null)
                        ? p.getSellPriceSecondary() : BigDecimal.ZERO;
                BigDecimal saleValue = BigDecimal.valueOf(Math.abs(m.getQuantity())).multiply(sellPrice);
                soldByMonth.merge(ym, saleValue, BigDecimal::add);
            }
        }

        // Build result list
        return buckets.stream().map(ym -> {
            BigDecimal added = addedByMonth.getOrDefault(ym, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal sold  = soldByMonth.getOrDefault(ym, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            return MonthlyFlowDTO.builder()
                    .month(ym.atDay(1).format(labelFmt))
                    .stockAddedValue(added)
                    .stockSoldValue(sold)
                    .netChange(added.subtract(sold).setScale(2, RoundingMode.HALF_UP))
                    .build();
        }).collect(Collectors.toList());
    }

    private BigDecimal totalValFromRatio(double ratio, BigDecimal totalCost) {
        return totalCost.multiply(BigDecimal.valueOf(ratio));
    }
}
