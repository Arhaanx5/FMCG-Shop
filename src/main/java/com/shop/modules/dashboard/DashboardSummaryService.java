package com.shop.modules.dashboard;

import com.shop.modules.billing.Bill;
import com.shop.modules.billing.BillRepository;
import com.shop.modules.billing.BillStatus;
import com.shop.modules.billing.BillItem;
import com.shop.modules.billing.dto.BillResponse;
import com.shop.modules.customer.Customer;
import com.shop.modules.customer.CustomerRepository;
import com.shop.modules.customer.CustomerService;
import com.shop.modules.delivery.DeliveryRepository;
import com.shop.modules.delivery.DeliveryStatus;
import com.shop.modules.expense.Expense;
import com.shop.modules.expense.ExpenseRepository;
import com.shop.modules.product.Product;
import com.shop.modules.product.ProductRepository;
import com.shop.modules.stock.Stock;
import com.shop.modules.stock.StockRepository;
import com.shop.modules.stock.StockBatch;
import com.shop.modules.stock.StockBatchRepository;
import com.shop.modules.dashboard.dto.DashboardResponse;
import com.shop.modules.dashboard.dto.MonthlyReportResponse;
import com.shop.modules.dashboard.dto.DashboardSummaryResponse;
import com.shop.modules.dashboard.dto.DailyTrendPoint;
import com.shop.modules.damage.DamageLogRepository;
import com.shop.modules.khata.Payment;
import com.shop.modules.khata.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardSummaryService {

    private final BillRepository billRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final StockBatchRepository batchRepository;
    private final StockRepository stockRepository;
    private final DeliveryRepository deliveryRepository;
    private final ExpenseRepository expenseRepository;
    private final CustomerService customerService;
    private final DashboardCalculationHelper dashboardCalculationHelper;
    private final SalesReportService salesReportService;
    private final com.shop.modules.billing.BillService billService;
    private final DamageLogRepository damageLogRepository;
    private final PaymentRepository paymentRepository;


    public DashboardResponse getTodaySummary() {
        // Today range
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        List<Bill> allTodayBills = billRepository.findBillsBetween(start, end);

        List<Bill> todayBills = allTodayBills.stream()
                .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                .collect(Collectors.toList());

        BigDecimal todayRevenue = todayBills.stream()
                .map(b -> b.getGrandTotal() != null ? b.getGrandTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CollectionBreakdown todayCol = dashboardCalculationHelper.calculateCollectionBreakdown(todayBills, start, end);
        BigDecimal todayCollected = todayCol.getTotalCollected();

        BigDecimal todayNewUdhar = todayBills.stream()
                .filter(b -> b.getPaymentMode() == com.shop.modules.billing.PaymentMode.UDHAR || b.getPaymentMode() == com.shop.modules.billing.PaymentMode.PARTIAL || b.getPaymentMode() == com.shop.modules.billing.PaymentMode.COD)
                .map(b -> b.getPendingAmount() != null ? b.getPendingAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal todayPending = customerRepository.getTotalPendingBalance();
        if (todayPending == null) {
            todayPending = BigDecimal.ZERO;
        }

        // Month range
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        List<Bill> monthBills = billRepository.findBillsBetween(monthStart, end).stream()
                .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                .collect(Collectors.toList());

        BigDecimal monthRevenue = monthBills.stream()
                .map(b -> b.getGrandTotal() != null ? b.getGrandTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Month expenses
        List<Expense> monthExpenses = expenseRepository.findBetween(LocalDate.now().withDayOfMonth(1), LocalDate.now());

        BigDecimal totalExpenses = monthExpenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthCogs = BigDecimal.ZERO;
        for (Bill bill : monthBills) {
            if (bill.getStatus() == BillStatus.CANCELLED) {
                continue;
            }
            for (BillItem item : bill.getItems()) {
                int totalQty = item.getQuantity() + item.getFreeQuantity();
                boolean isPrimary = item.getUnitType() != null && item.getProduct().getPrimaryUnit() != null 
                        && item.getUnitType().name().equalsIgnoreCase(item.getProduct().getPrimaryUnit());
                int scale = isPrimary ? (item.getProduct().getSecondaryPerPrimary() != null ? item.getProduct().getSecondaryPerPrimary() : 1) : 1;
                int totalQtyInSec = totalQty * scale;
                BigDecimal buyPricePerSec = BigDecimal.ZERO;
                if (item.getBatch() != null) {
                    buyPricePerSec = item.getBatch().getBuyPricePerSecondary(item.getProduct().getSecondaryPerPrimary());
                } else {
                    buyPricePerSec = item.getProduct().getBuyPricePerSecondary();
                }
                BigDecimal cost = buyPricePerSec.multiply(BigDecimal.valueOf(totalQtyInSec));
                monthCogs = monthCogs.add(cost);
            }
        }

        BigDecimal opex = monthExpenses.stream()
                .filter(e -> e.getCategory() != com.shop.modules.expense.ExpenseCategory.STOCK_PURCHASE)
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netProfit = monthRevenue.subtract(monthCogs).subtract(opex);

        // Alerts
        long lowStockCount = productRepository.findLowStockProducts().size();
        long expiringCount = batchRepository.findExpiringBefore(LocalDate.now().plusDays(7)).size();
        long inactiveCount = customerRepository.findInactiveCustomers(LocalDateTime.now().minusDays(30)).size();
        long pendingDeliveries = deliveryRepository.findByStatus(DeliveryStatus.PENDING).size();

        // Low stock items list
        List<Stock> allStocks = stockRepository.findAll();
        Map<UUID, Integer> stockMap = allStocks.stream()
                .filter(s -> s.getProduct() != null)
                .collect(Collectors.toMap(s -> s.getProduct().getId(), Stock::getTotalSecondaryUnits, (a, b) -> a));

        List<DashboardResponse.LowStockAlert> alerts = productRepository.findLowStockProducts().stream()
                .map(p -> {
                    int current = stockMap.getOrDefault(p.getId(), 0);
                    return DashboardResponse.LowStockAlert.builder()
                            .productName(p.getName())
                            .brand(p.getBrand())
                            .category(p.getCategory().name())
                            .currentStock(current)
                            .threshold(p.getLowStockAlert())
                            .unit(p.getSecondaryUnit())
                            .build();
                })
                .collect(Collectors.toList());

        // Expiring batches list (lookahead extended to 30 days)
        List<DashboardResponse.ExpiringBatchAlert> expiringBatches = batchRepository.findExpiringBefore(LocalDate.now().plusDays(30)).stream()
                .map(b -> DashboardResponse.ExpiringBatchAlert.builder()
                        .productName(b.getProduct().getName())
                        .batchNo(b.getBatchNumber())
                        .expiryDate(b.getExpiryDate())
                        .stockCount(b.getSecondaryRemaining())
                        .build())
                .collect(Collectors.toList());

        // Inactive customers list (cutoff extended to 15 days)
        List<DashboardResponse.InactiveCustomerAlert> inactiveCustomers = customerRepository.findInactiveCustomers(LocalDateTime.now().minusDays(15)).stream()
                .map(c -> DashboardResponse.InactiveCustomerAlert.builder()
                        .customerName(c.getName())
                        .shopName(c.getShopName())
                        .phone(c.getPhone())
                        .lastOrderDate(c.getLastOrderAt())
                        .build())
                .collect(Collectors.toList());

        // Pending deliveries list
        List<DashboardResponse.PendingDeliveryAlert> pendingDeliveriesList = deliveryRepository.findByStatus(DeliveryStatus.PENDING).stream()
                .map(d -> DashboardResponse.PendingDeliveryAlert.builder()
                        .billNumber(d.getBill() != null ? d.getBill().getBillNumber() : null)
                        .customerName(d.getBill() != null && d.getBill().getCustomer() != null ? d.getBill().getCustomer().getName() : null)
                        .shopName(d.getBill() != null && d.getBill().getCustomer() != null ? d.getBill().getCustomer().getShopName() : null)
                        .amount(d.getBill() != null ? d.getBill().getGrandTotal() : null)
                        .build())
                .collect(Collectors.toList());

        // Overdue Udhar calculation
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        List<Bill> pendingBills = billRepository.findPendingBills();
        List<Bill> overdueBills = pendingBills.stream()
                .filter(b -> b.getCreatedAt() != null && b.getCreatedAt().isBefore(threshold))
                .collect(Collectors.toList());

        Map<Customer, List<Bill>> customerOverdueBills = overdueBills.stream()
                .filter(b -> b.getCustomer() != null)
                .collect(Collectors.groupingBy(Bill::getCustomer));

        List<DashboardResponse.OverdueUdharAlert> overdueUdharAlerts = customerOverdueBills.entrySet().stream()
                .map(entry -> {
                    Customer c = entry.getKey();
                    List<Bill> bills = entry.getValue();

                    BigDecimal totalOverdue = bills.stream()
                            .map(b -> b.getPendingAmount() != null ? b.getPendingAmount() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    LocalDateTime oldestBillTime = bills.stream()
                            .map(Bill::getCreatedAt)
                            .min(Comparator.naturalOrder())
                            .orElse(LocalDateTime.now());

                    long daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(oldestBillTime, LocalDateTime.now());

                    return DashboardResponse.OverdueUdharAlert.builder()
                            .customerName(c.getName())
                            .shopName(c.getShopName())
                            .overdueDays((int) daysOverdue)
                            .totalOverdueAmount(totalOverdue)
                            .build();
                })
                .sorted((a, b) -> b.getTotalOverdueAmount().compareTo(a.getTotalOverdueAmount()))
                .collect(Collectors.toList());

        long overdueUdharCount = overdueUdharAlerts.size();

        // Credit Limit Exceeded calculation (L-3 N+1 double-call bug optimized here)
        List<Customer> activeCustomers = customerRepository.findByActiveTrue();
        List<DashboardResponse.CreditLimitAlert> creditLimitAlerts = activeCustomers.stream()
                .map(c -> {
                    BigDecimal limit = customerService.calculateEffectiveCreditLimit(c);
                    return new Object[] { c, limit };
                })
                .filter(arr -> {
                    Customer c = (Customer) arr[0];
                    BigDecimal limit = (BigDecimal) arr[1];
                    return c.getTotalPending() != null && c.getTotalPending().compareTo(limit) > 0;
                })
                .map(arr -> {
                    Customer c = (Customer) arr[0];
                    BigDecimal limit = (BigDecimal) arr[1];
                    return DashboardResponse.CreditLimitAlert.builder()
                            .customerName(c.getName())
                            .shopName(c.getShopName())
                            .totalPending(c.getTotalPending())
                            .creditLimit(limit)
                            .build();
                })
                .sorted((a, b) -> b.getTotalPending().subtract(b.getCreditLimit()).compareTo(a.getTotalPending().subtract(a.getCreditLimit())))
                .collect(Collectors.toList());

        long creditLimitExceededCount = creditLimitAlerts.size();

        // Yesterday comparison
        LocalDateTime yesterdayStart = start.minusDays(1);
        LocalDateTime yesterdayEnd = start;
        List<Bill> yesterdayBillsList = billRepository.findBillsBetweenBasic(yesterdayStart, yesterdayEnd).stream()
                .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                .collect(Collectors.toList());

        BigDecimal yesterdayRevenue = yesterdayBillsList.stream()
                .map(b -> b.getGrandTotal() != null ? b.getGrandTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CollectionBreakdown yesterdayCol = dashboardCalculationHelper.calculateCollectionBreakdown(yesterdayBillsList, yesterdayStart, yesterdayEnd);

        BigDecimal yesterdayNewUdhar = yesterdayBillsList.stream()
                .filter(b -> b.getPaymentMode() == com.shop.modules.billing.PaymentMode.UDHAR || b.getPaymentMode() == com.shop.modules.billing.PaymentMode.PARTIAL || b.getPaymentMode() == com.shop.modules.billing.PaymentMode.COD)
                .map(b -> b.getPendingAmount() != null ? b.getPendingAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Always current stats
        List<Bill> codBills = billRepository.findByStatusIn(List.of(BillStatus.COD_PENDING, BillStatus.COD_DELIVERED));
        BigDecimal codPendingAmount = codBills.stream()
                .map(b -> b.getPendingAmount() != null ? b.getPendingAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long codPendingCount = codBills.size();

        LocalDateTime codOverdueCutoff = LocalDateTime.now().minusDays(7);
        int codOverdueCount = (int) codBills.stream()
                .filter(b -> b.getCreatedAt() != null && b.getCreatedAt().isBefore(codOverdueCutoff))
                .count();

        long npaCustomersCount = activeCustomers.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsNpa()))
                .count();
        BigDecimal npaCustomersAmount = activeCustomers.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsNpa()))
                .map(c -> c.getTotalPending() != null ? c.getTotalPending() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long oldestPendingDays = 0;
        if (!pendingBills.isEmpty()) {
            LocalDateTime oldestBillTime = pendingBills.stream()
                    .map(Bill::getCreatedAt)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            if (oldestBillTime != null) {
                oldestPendingDays = java.time.temporal.ChronoUnit.DAYS.between(oldestBillTime, LocalDateTime.now());
            }
        }
        BigDecimal totalOutstandingUdhar = activeCustomers.stream()
                .map(c -> c.getTotalPending() != null ? c.getTotalPending() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalInventoryValue = dashboardCalculationHelper.calculateTotalInventoryValue();

        BigDecimal netProfitMarginPct = BigDecimal.ZERO;
        if (monthRevenue.compareTo(BigDecimal.ZERO) > 0) {
            netProfitMarginPct = netProfit.multiply(BigDecimal.valueOf(100)).divide(monthRevenue, 2, RoundingMode.HALF_UP);
        }

        BigDecimal avgBillValue = BigDecimal.ZERO;
        if (!todayBills.isEmpty()) {
            avgBillValue = todayRevenue.divide(BigDecimal.valueOf(todayBills.size()), 2, RoundingMode.HALF_UP);
        }

        BigDecimal monthDamageLoss = damageLogRepository.getTotalDamageLoss(monthStart, end);
        if (monthDamageLoss == null) {
            monthDamageLoss = BigDecimal.ZERO;
        }

        long newCustomersThisMonth = activeCustomers.stream()
                .filter(c -> c.getCreatedAt() != null && c.getCreatedAt().isAfter(monthStart) && c.getCreatedAt().isBefore(end))
                .count();

        List<Bill> allCodBills = billRepository.findAll().stream()
                .filter(b -> b.getPaymentMode() == com.shop.modules.billing.PaymentMode.COD)
                .collect(Collectors.toList());
        BigDecimal codSuccessRate = BigDecimal.ZERO;
        if (!allCodBills.isEmpty()) {
            long successCount = allCodBills.stream()
                    .filter(b -> b.getStatus() == BillStatus.COD_COLLECTED || b.getStatus() == BillStatus.PAID)
                    .count();
            codSuccessRate = BigDecimal.valueOf(successCount * 100).divide(BigDecimal.valueOf(allCodBills.size()), 2, RoundingMode.HALF_UP);
        }

        List<Payment> monthPayments = paymentRepository.findBetween(monthStart, end);
        BigDecimal avgCollectionDays = dashboardCalculationHelper.calculateAvgCollectionDays(monthPayments);

        LocalDateTime yearStart = LocalDate.now().withDayOfYear(1).atStartOfDay();
        List<Bill> yearBills = billRepository.findBillsBetween(yearStart, end).stream()
                .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                .collect(Collectors.toList());

        long activeCustomersTodayCount = todayBills.stream().map(Bill::getCustomer).filter(c -> c != null).map(Customer::getId).distinct().count();
        long activeCustomersMonthCount = monthBills.stream().map(Bill::getCustomer).filter(c -> c != null).map(Customer::getId).distinct().count();
        long activeCustomersYearCount = yearBills.stream().map(Bill::getCustomer).filter(c -> c != null).map(Customer::getId).distinct().count();

        List<DailyTrendPoint> sevenDayTrend = salesReportService.getTrendData(7);

        return DashboardResponse.builder()
                .todayRevenue(todayRevenue)
                .todayCollected(todayCollected)
                .todayCollectedCash(todayCol.getCollectedCash())
                .todayCollectedUpi(todayCol.getCollectedUpi())
                .todayCollectedUdhar(todayCol.getCollectedUdhar())
                .todayPending(todayPending)
                .todayBills((long) todayBills.size())
                .totalInventoryValue(totalInventoryValue)
                .todayNewUdhar(todayNewUdhar)
                .codPendingAmount(codPendingAmount)
                .codPendingBillsCount(codPendingCount)
                .todayExpenses(opex)
                .monthRevenue(monthRevenue)
                .monthExpenses(totalExpenses)
                .monthNetProfit(netProfit)
                .lowStockCount(lowStockCount)
                .expiringBatchesCount(expiringCount)
                .inactiveCustomersCount(inactiveCount)
                .pendingDeliveriesCount(pendingDeliveries)
                .overdueUdharCount(overdueUdharCount)
                .creditLimitExceededCount(creditLimitExceededCount)
                .backupStale(null)  // Decoupled to dedicated endpoint
                .lastBackupTime(null)  // Decoupled to dedicated endpoint
                .lowStockAlerts(alerts)
                .expiringBatches(expiringBatches)
                .inactiveCustomers(inactiveCustomers)
                .pendingDeliveries(pendingDeliveriesList)
                .overdueUdharAlerts(overdueUdharAlerts)
                .creditLimitExceededAlerts(creditLimitAlerts)
                .yesterdayRevenue(yesterdayRevenue)
                .yesterdayCollection(yesterdayCol.getTotalCollected())
                .yesterdayBills((long) yesterdayBillsList.size())
                .yesterdayCash(yesterdayCol.getCollectedCash())
                .yesterdayUPI(yesterdayCol.getCollectedUpi())
                .yesterdayUdharRecovery(yesterdayCol.getCollectedUdhar())
                .yesterdayNewUdhar(yesterdayNewUdhar)
                .codOverdueCount(codOverdueCount)
                .npaCustomersCount(npaCustomersCount)
                .npaCustomersAmount(npaCustomersAmount)
                .oldestPendingDays(oldestPendingDays)
                .totalOutstandingUdhar(totalOutstandingUdhar)
                .totalNewUdhar(todayNewUdhar)
                .totalExpenses(totalExpenses)
                .todayCashCollection(todayCol.getCollectedCash())
                .todayUPICollection(todayCol.getCollectedUpi())
                .todayUdharRecovery(todayCol.getCollectedUdhar())
                .totalWaived(todayCol.getWaivedAmount())
                .netProfitMarginPct(netProfitMarginPct)
                .avgBillValue(avgBillValue)
                .damageLossMTD(monthDamageLoss)
                .newCustomersThisMonth(newCustomersThisMonth)
                .codSuccessRate(codSuccessRate)
                .avgCollectionDays(avgCollectionDays)
                .activeCustomersToday(activeCustomersTodayCount)
                .activeCustomersMonth(activeCustomersMonthCount)
                .activeCustomersYear(activeCustomersYearCount)
                .sevenDayTrend(sevenDayTrend)
                .build();
    }

    public DashboardSummaryResponse getDashboardSummary(int year, int month, int limit) {
        DashboardResponse today = getTodaySummary();
        MonthlyReportResponse monthly = salesReportService.getMonthlyReport(year, month);
        MonthlyReportResponse yearly = salesReportService.getYearlyReport(year);
        List<BillResponse> recentBills = billService.getRecentBills(limit);

        return DashboardSummaryResponse.builder()
                .today(today)
                .monthly(monthly)
                .yearly(yearly)
                .recentBills(recentBills)
                .build();
    }
}
