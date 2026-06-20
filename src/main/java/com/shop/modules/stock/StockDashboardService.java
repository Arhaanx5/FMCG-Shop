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
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockDashboardService {

    private final ProductRepository productRepository;
    private final StockBatchRepository batchRepository;
    private final StockRepository stockRepository;
    private final StockBIService biService;

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

    public DashboardSummary getDashboardSummary() {
        List<Product> products = productRepository.findAll();
        List<StockBatch> activeBatches = batchRepository.findAll().stream()
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

        List<RecentBatchDTO> recentDtos = batchRepository.findAll().stream()
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

            // Incl. GST cost
            BigDecimal buyPriceWithTaxPerSec = (b.getBuyPriceWithTax() != null)
                    ? b.getBuyPriceWithTax().divide(BigDecimal.valueOf(ratio), 4, RoundingMode.HALF_UP)
                    : costPrice.multiply(BigDecimal.ONE.add((b.getGstPercent() != null ? b.getGstPercent() : BigDecimal.ZERO).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)));
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

        for (Product p : products) {
            Stock stock = stockRepository.findByProductId(p.getId()).orElse(null);
            int qty = stock != null ? stock.getTotalSecondaryUnits() : 0;
            if (qty > 0) {
                activeSkus++;
            } else {
                outOfStockCount++;
            }
            if (qty <= p.getLowStockAlertInSecondary()) {
                lowStockCount++;
            }
        }

        BigDecimal expectedProfit = mrpValue.subtract(costValue);

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
                .build();
    }

    private BigDecimal totalValFromRatio(double ratio, BigDecimal totalCost) {
        return totalCost.multiply(BigDecimal.valueOf(ratio));
    }
}
