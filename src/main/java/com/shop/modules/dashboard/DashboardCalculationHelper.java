package com.shop.modules.dashboard;

import com.shop.modules.billing.Bill;
import com.shop.modules.billing.BillRepository;
import com.shop.modules.billing.BillStatus;
import com.shop.modules.product.Product;
import com.shop.modules.product.ProductRepository;
import com.shop.modules.khata.Payment;
import com.shop.modules.khata.PaymentRepository;
import com.shop.modules.stock.Stock;
import com.shop.modules.stock.StockRepository;
import com.shop.modules.stock.StockBatch;
import com.shop.modules.stock.StockBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DashboardCalculationHelper {

    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final StockBatchRepository batchRepository;
    private final StockRepository stockRepository;

    public CollectionBreakdown calculateCollectionBreakdown(List<Bill> bills, LocalDateTime start, LocalDateTime end) {
        List<Payment> periodPayments = paymentRepository.findBetween(start, end);

        BigDecimal collectedUdhar = periodPayments.stream()
                .filter(p -> !"WAIVE_OFF".equalsIgnoreCase(p.getPaymentMode()) && !"REFUND".equalsIgnoreCase(p.getPaymentMode()))
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal khataCash = periodPayments.stream()
                .filter(p -> "CASH".equalsIgnoreCase(p.getPaymentMode()))
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal khataUpi = periodPayments.stream()
                .filter(p -> "UPI".equalsIgnoreCase(p.getPaymentMode()))
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal waivedAmount = periodPayments.stream()
                .filter(p -> "WAIVE_OFF".equalsIgnoreCase(p.getPaymentMode()))
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal collectedRefunds = periodPayments.stream()
                .filter(p -> "REFUND".equalsIgnoreCase(p.getPaymentMode()) && (p.getNotes() == null || !p.getNotes().contains("Store credit")))
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal immediateCash = BigDecimal.ZERO;
        BigDecimal immediateUpi = BigDecimal.ZERO;

        Map<UUID, BigDecimal> appliedAmountsMap = new HashMap<>();
        if (!bills.isEmpty()) {
            List<UUID> billIds = bills.stream().map(Bill::getId).collect(Collectors.toList());
            List<Payment> appliedPayments = paymentRepository.findByBillIdIn(billIds);
            for (Payment p : appliedPayments) {
                if (p.getBill() != null) {
                    UUID bId = p.getBill().getId();
                    BigDecimal amt = p.getAppliedAmount() != null ? p.getAppliedAmount() : BigDecimal.ZERO;
                    appliedAmountsMap.put(bId, appliedAmountsMap.getOrDefault(bId, BigDecimal.ZERO).add(amt));
                }
            }
        }

        for (Bill bill : bills) {
            BigDecimal billPaidAmount = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal applied = appliedAmountsMap.getOrDefault(bill.getId(), BigDecimal.ZERO);
            BigDecimal netImmediate = billPaidAmount.subtract(applied);
            if (netImmediate.compareTo(BigDecimal.ZERO) > 0) {
                if (bill.getPaymentMode() != null && "UPI".equalsIgnoreCase(bill.getPaymentMode().name())) {
                    immediateUpi = immediateUpi.add(netImmediate);
                } else if (bill.getPaymentMode() != null && "PARTIAL".equalsIgnoreCase(bill.getPaymentMode().name())
                        && "UPI".equalsIgnoreCase(bill.getPartialPaymentMode())) {
                    immediateUpi = immediateUpi.add(netImmediate);
                } else {
                    immediateCash = immediateCash.add(netImmediate);
                }
            }
        }

        BigDecimal collectedCash = khataCash.add(immediateCash).add(collectedRefunds);
        BigDecimal collectedUpi = khataUpi.add(immediateUpi);
        BigDecimal totalCollected = collectedCash.add(collectedUpi);

        return CollectionBreakdown.builder()
                .totalCollected(totalCollected)
                .collectedCash(collectedCash)
                .collectedUpi(collectedUpi)
                .collectedUdhar(collectedUdhar)
                .waivedAmount(waivedAmount)
                .build();
    }

    public BigDecimal calculateTotalInventoryValue() {
        List<Product> products = productRepository.findAll();
        List<StockBatch> allBatches = batchRepository.findAll().stream()
                .filter(b -> b.getExhausted() == null || !b.getExhausted())
                .collect(Collectors.toList());
        Map<UUID, List<StockBatch>> batchesByProduct = allBatches.stream()
                .filter(b -> b.getProduct() != null)
                .collect(Collectors.groupingBy(b -> b.getProduct().getId()));

        List<Stock> allStocks = stockRepository.findAll();
        Map<UUID, Stock> stockByProduct = allStocks.stream()
                .filter(s -> s.getProduct() != null)
                .collect(Collectors.toMap(s -> s.getProduct().getId(), s -> s, (s1, s2) -> s1));

        BigDecimal totalValue = BigDecimal.ZERO;
        for (Product p : products) {
            List<StockBatch> activeBatches = batchesByProduct.getOrDefault(p.getId(), Collections.emptyList());

            BigDecimal avgCost = BigDecimal.ZERO;
            if (activeBatches.isEmpty()) {
                avgCost = p.getBuyPricePerSecondary() != null ? p.getBuyPricePerSecondary() : BigDecimal.ZERO;
            } else {
                int totalQty = 0;
                BigDecimal totalCost = BigDecimal.ZERO;
                for (StockBatch b : activeBatches) {
                    int qty = b.getSecondaryRemaining() != null ? b.getSecondaryRemaining() : 0;
                    if (qty > 0) {
                        totalQty += qty;
                        int ratio = p.getSecondaryPerPrimary() != null ? p.getSecondaryPerPrimary() : 1;
                        BigDecimal unitPrice = b.getBuyPricePerSecondary(ratio);
                        totalCost = totalCost.add(BigDecimal.valueOf(qty).multiply(unitPrice));
                    }
                }
                if (totalQty == 0) {
                    avgCost = p.getBuyPricePerSecondary() != null ? p.getBuyPricePerSecondary() : BigDecimal.ZERO;
                } else {
                    avgCost = totalCost.divide(BigDecimal.valueOf(totalQty), 2, RoundingMode.HALF_UP);
                }
            }

            Stock stock = stockByProduct.get(p.getId());
            int currentStock = stock != null ? stock.getTotalSecondaryUnits() : 0;
            BigDecimal value = BigDecimal.valueOf(currentStock).multiply(avgCost);
            totalValue = totalValue.add(value);
        }
        return totalValue.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateAvgCollectionDays(List<Payment> payments) {
        List<Payment> paymentsWithBills = payments.stream()
                .filter(p -> p.getBill() != null && p.getBill().getCreatedAt() != null && p.getPaidAt() != null)
                .collect(Collectors.toList());
        if (paymentsWithBills.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long totalDays = 0;
        for (Payment p : paymentsWithBills) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(p.getBill().getCreatedAt(), p.getPaidAt());
            totalDays += Math.max(0, days);
        }
        return BigDecimal.valueOf(totalDays).divide(BigDecimal.valueOf(paymentsWithBills.size()), 2, RoundingMode.HALF_UP);
    }
}
