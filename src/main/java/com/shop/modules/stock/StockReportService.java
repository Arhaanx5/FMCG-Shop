package com.shop.modules.stock;

import com.shop.modules.product.Product;
import com.shop.modules.product.ProductRepository;
import com.shop.modules.product.Category;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.shop.modules.billing.BillItem;
import com.shop.modules.billing.BillRepository;

@Service
@RequiredArgsConstructor
public class StockReportService {

    private final ProductRepository productRepository;
    private final StockBatchRepository batchRepository;
    private final StockRepository stockRepository;
    private final BillRepository billRepository;
    private final StockInventoryService inventoryService;

    public BigDecimal calculateWeightedAvgCost(UUID productId) {
        List<StockBatch> activeBatches = batchRepository.findByProductId(productId).stream()
                .filter(b -> b.getExhausted() == null || !b.getExhausted())
                .collect(Collectors.toList());

        if (activeBatches.isEmpty()) {
            Product p = productRepository.findById(productId).orElse(null);
            if (p != null) {
                return p.getBuyPricePerSecondary();
            }
            return BigDecimal.ZERO;
        }

        int totalQty = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (StockBatch b : activeBatches) {
            int qty = b.getSecondaryRemaining() != null ? b.getSecondaryRemaining() : 0;
            if (qty > 0) {
                totalQty += qty;
                Product p = b.getProduct();
                int ratio = p != null && p.getSecondaryPerPrimary() != null ? p.getSecondaryPerPrimary() : 1;
                BigDecimal unitPrice = b.getBuyPricePerSecondary(ratio);
                totalCost = totalCost.add(BigDecimal.valueOf(qty).multiply(unitPrice));
            }
        }

        if (totalQty == 0) {
            Product p = activeBatches.get(0).getProduct();
            return p.getBuyPricePerSecondary();
        }

        return totalCost.divide(BigDecimal.valueOf(totalQty), 2, RoundingMode.HALF_UP);
    }

    @Data
    @Builder
    public static class InventoryReportRow {
        private UUID productId;
        private String productName;
        private String category;
        private String brand;
        private int currentStock;
        private BigDecimal avgCost;
        private BigDecimal sellingPrice;
        private BigDecimal marginPercent;
        private BigDecimal inventoryValue;
        private String status; // Healthy, Low Stock, Out of Stock, Overstock, Dead Stock
    }

    public Page<InventoryReportRow> getInventoryValuationReport(Pageable pageable) {
        Page<Product> products = productRepository.findAll(pageable);
        List<InventoryReportRow> rows = products.getContent().stream().map(p -> {
            Stock stock = inventoryService.getOrCreateStock(p.getId());
            BigDecimal avgCost = calculateWeightedAvgCost(p.getId());
            BigDecimal sellPrice = p.getSellPriceSecondary() != null ? p.getSellPriceSecondary() : BigDecimal.ZERO;

            BigDecimal margin = BigDecimal.ZERO;
            if (sellPrice.compareTo(BigDecimal.ZERO) > 0) {
                margin = sellPrice.subtract(avgCost)
                        .divide(sellPrice, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            BigDecimal value = BigDecimal.valueOf(stock.getTotalSecondaryUnits()).multiply(avgCost);

            int alertThreshold = p.getLowStockAlertInSecondary();
            String status = "Healthy";
            int totalQty = stock.getTotalSecondaryUnits();
            int ratio = p.getSecondaryPerPrimary() != null ? p.getSecondaryPerPrimary() : 1;

            if (totalQty <= 0) {
                status = "Out Of Stock";
            } else if (totalQty <= alertThreshold) {
                status = "Low Stock";
            } else if (totalQty > alertThreshold * 8 && totalQty > ratio * 5) {
                status = "Overstock";
            }

            return InventoryReportRow.builder()
                    .productId(p.getId())
                    .productName(p.getName())
                    .category(p.getCategory() != null ? p.getCategory().name() : "OTHER")
                    .brand(p.getBrand())
                    .currentStock(totalQty)
                    .avgCost(avgCost)
                    .sellingPrice(sellPrice)
                    .marginPercent(margin.setScale(2, RoundingMode.HALF_UP))
                    .inventoryValue(value.setScale(2, RoundingMode.HALF_UP))
                    .status(status)
                    .build();
        }).collect(Collectors.toList());

        return new PageImpl<>(rows, pageable, products.getTotalElements());
    }

    @Data
    @Builder
    public static class ExpiryReportRow {
        private UUID batchId;
        private String batchNumber;
        private String productName;
        private LocalDate expiryDate;
        private int remainingQty;
        private BigDecimal costValue;
        private long daysToExpiry;
        private String riskBucket;
    }

    public Page<ExpiryReportRow> getExpiryReport(Pageable pageable) {
        Page<StockBatch> batches = batchRepository.findAll(pageable);
        LocalDate today = LocalDate.now();
        List<ExpiryReportRow> rows = batches.getContent().stream()
                .filter(b -> b.getSecondaryRemaining() > 0)
                .map(b -> {
                    Product p = b.getProduct();
                    int ratio = p != null && p.getSecondaryPerPrimary() != null ? p.getSecondaryPerPrimary() : 1;
                    BigDecimal cost = b.getBuyPricePerSecondary(ratio);
                    BigDecimal value = BigDecimal.valueOf(b.getSecondaryRemaining()).multiply(cost);

                    long days = ChronoUnit.DAYS.between(today, b.getExpiryDate());
                    String bucket = "Healthy";
                    if (days < 0) {
                        bucket = "Expired";
                    } else if (days <= 7) {
                        bucket = "Expiring in 7 Days";
                    } else if (days <= 15) {
                        bucket = "Expiring in 15 Days";
                    } else if (days <= 30) {
                        bucket = "Expiring in 30 Days";
                    } else if (days <= 60) {
                        bucket = "Expiring in 60 Days";
                    } else if (days <= 90) {
                        bucket = "Expiring in 90 Days";
                    }

                    return ExpiryReportRow.builder()
                            .batchId(b.getId())
                            .batchNumber(b.getBatchNumber())
                            .productName(p != null ? p.getName() : "Unknown")
                            .expiryDate(b.getExpiryDate())
                            .remainingQty(b.getSecondaryRemaining())
                            .costValue(value.setScale(2, RoundingMode.HALF_UP))
                            .daysToExpiry(days)
                            .riskBucket(bucket)
                            .build();
                }).collect(Collectors.toList());

        return new PageImpl<>(rows, pageable, batches.getTotalElements());
    }

    @Data
    @Builder
    public static class AgingReportRow {
        private String batchNumber;
        private String productName;
        private LocalDate stockReceivedDate;
        private int remainingQty;
        private long ageDays;
        private String ageBucket;
    }

    public Page<AgingReportRow> getStockAgingReport(Pageable pageable) {
        Page<StockBatch> batches = batchRepository.findAll(pageable);
        LocalDate today = LocalDate.now();
        List<AgingReportRow> rows = batches.getContent().stream()
                .filter(b -> b.getSecondaryRemaining() > 0)
                .map(b -> {
                    LocalDate receivedDate = b.getStockReceivedDate() != null ? b.getStockReceivedDate() : b.getReceivedAt().toLocalDate();
                    long age = ChronoUnit.DAYS.between(receivedDate, today);
                    String bucket = "0-30 Days";
                    if (age > 180) {
                        bucket = "180+ Days";
                    } else if (age > 90) {
                        bucket = "91-180 Days";
                    } else if (age > 60) {
                        bucket = "61-90 Days";
                    } else if (age > 30) {
                        bucket = "31-60 Days";
                    }

                    return AgingReportRow.builder()
                            .batchNumber(b.getBatchNumber())
                            .productName(b.getProduct() != null ? b.getProduct().getName() : "Unknown")
                            .stockReceivedDate(receivedDate)
                            .remainingQty(b.getSecondaryRemaining())
                            .ageDays(age)
                            .ageBucket(bucket)
                            .build();
                }).collect(Collectors.toList());

        return new PageImpl<>(rows, pageable, batches.getTotalElements());
    }

    @Data
    @Builder
    public static class CategoryProfitabilityRow {
        private String categoryName;
        private BigDecimal costValue;
        private BigDecimal sellingValue;
        private BigDecimal profitPotential;
        private BigDecimal marginPercent;
    }

    public List<CategoryProfitabilityRow> getCategoryProfitabilityReport() {
        List<Product> products = productRepository.findAll();
        java.util.Map<Category, List<Product>> byCategory = products.stream()
                .collect(Collectors.groupingBy(Product::getCategory));

        List<CategoryProfitabilityRow> report = new ArrayList<>();
        for (java.util.Map.Entry<Category, List<Product>> entry : byCategory.entrySet()) {
            BigDecimal totalCost = BigDecimal.ZERO;
            BigDecimal totalSell = BigDecimal.ZERO;

            for (Product p : entry.getValue()) {
                Stock stock = inventoryService.getOrCreateStock(p.getId());
                int qty = stock.getTotalSecondaryUnits();
                if (qty > 0) {
                    BigDecimal cost = calculateWeightedAvgCost(p.getId());
                    BigDecimal sell = p.getSellPriceSecondary() != null ? p.getSellPriceSecondary() : BigDecimal.ZERO;
                    totalCost = totalCost.add(BigDecimal.valueOf(qty).multiply(cost));
                    totalSell = totalSell.add(BigDecimal.valueOf(qty).multiply(sell));
                }
            }

            BigDecimal profit = totalSell.subtract(totalCost);
            BigDecimal margin = BigDecimal.ZERO;
            if (totalSell.compareTo(BigDecimal.ZERO) > 0) {
                margin = profit.divide(totalSell, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            }

            report.add(CategoryProfitabilityRow.builder()
                    .categoryName(entry.getKey().name())
                    .costValue(totalCost.setScale(2, RoundingMode.HALF_UP))
                    .sellingValue(totalSell.setScale(2, RoundingMode.HALF_UP))
                    .profitPotential(profit.setScale(2, RoundingMode.HALF_UP))
                    .marginPercent(margin.setScale(2, RoundingMode.HALF_UP))
                    .build());
        }
        return report;
    }
}
