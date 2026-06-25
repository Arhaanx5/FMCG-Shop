package com.shop.modules.dashboard;

import com.shop.modules.billing.Bill;
import com.shop.modules.billing.BillItem;
import com.shop.modules.billing.BillRepository;
import com.shop.modules.billing.BillStatus;
import com.shop.modules.billing.BillService;
import com.shop.modules.billing.dto.BillResponse;
import com.shop.modules.customer.CustomerRepository;
import com.shop.modules.customer.Customer;
import com.shop.modules.dashboard.dto.DashboardResponse;
import com.shop.modules.dashboard.dto.MonthlyReportResponse;
import com.shop.modules.dashboard.dto.DashboardSummaryResponse;
import com.shop.modules.dashboard.dto.DailyTrendPoint;
import com.shop.modules.delivery.DeliveryRepository;
import com.shop.modules.delivery.DeliveryStatus;
import com.shop.modules.expense.Expense;
import com.shop.modules.expense.ExpenseRepository;
import com.shop.modules.product.Product;
import com.shop.modules.product.ProductRepository;
import com.shop.modules.stock.StockBatchRepository;
import com.shop.modules.stock.StockRepository;
import com.shop.modules.user.UserRepository;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRole;
import com.shop.modules.area.AreaRepository;
import com.shop.modules.area.Area;
import com.shop.modules.khata.PaymentRepository;
import com.shop.modules.khata.Payment;
import com.shop.modules.dashboard.dto.SalesmanPerformanceResponse;
import com.shop.modules.damage.DamageLogRepository;
import com.shop.modules.customer.CustomerService;
import com.shop.modules.backup.BackupService;
import java.util.Comparator;
import java.util.ArrayList;
import lombok.Builder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final BillRepository billRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final StockBatchRepository batchRepository;
    private final StockRepository stockRepository;
    private final DeliveryRepository deliveryRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final AreaRepository areaRepository;
    private final PaymentRepository paymentRepository;
    private final DamageLogRepository damageLogRepository;
    private final BillService billService;
    private final CustomerService customerService;
    private final BackupService backupService;

    @Autowired
    public DashboardService(BillRepository billRepository,
                           CustomerRepository customerRepository,
                           ProductRepository productRepository,
                           StockBatchRepository batchRepository,
                           StockRepository stockRepository,
                           DeliveryRepository deliveryRepository,
                           ExpenseRepository expenseRepository,
                           UserRepository userRepository,
                           AreaRepository areaRepository,
                           PaymentRepository paymentRepository,
                           DamageLogRepository damageLogRepository,
                           BillService billService,
                           CustomerService customerService,
                           BackupService backupService) {
        this.billRepository = billRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
        this.stockRepository = stockRepository;
        this.deliveryRepository = deliveryRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.areaRepository = areaRepository;
        this.paymentRepository = paymentRepository;
        this.damageLogRepository = damageLogRepository;
        this.billService = billService;
        this.customerService = customerService;
        this.backupService = backupService;
    }

    @Builder
    @Data
    private static class CollectionBreakdown {
        private BigDecimal totalCollected;
        private BigDecimal collectedCash;
        private BigDecimal collectedUpi;
        private BigDecimal collectedUdhar;
        private BigDecimal waivedAmount;
    }

    public DashboardResponse getTodaySummary() {

        // Today range
        LocalDateTime start =
                LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        // Today bills (CONFIRMED, PARTIAL, and PAID are included in sales metrics)
        List<Bill> allTodayBills = billRepository.findBillsBetween(start, end);
        System.out.println("DEBUG TODAY BILLS INFO:");
        System.out.println("Total today bills in DB: " + allTodayBills.size());
        for (Bill b : allTodayBills) {
            System.out.println("Bill: " + b.getBillNumber() + " | Status: " + b.getStatus() + " | PaymentMode: " + b.getPaymentMode() + " | Pending: " + b.getPendingAmount());
        }

        List<Bill> todayBills = allTodayBills.stream()
                        .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                        .collect(Collectors.toList());

        BigDecimal todayRevenue = todayBills.stream()
                .map(b -> b.getGrandTotal() != null ? b.getGrandTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CollectionBreakdown todayCol = calculateCollectionBreakdown(todayBills, start, end);
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
        LocalDateTime monthStart =
                LocalDate.now().withDayOfMonth(1)
                        .atStartOfDay();

        List<Bill> monthBills =
                billRepository.findBillsBetween(monthStart, end).stream()
                        .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                        .collect(Collectors.toList());

        BigDecimal monthRevenue = monthBills.stream()
                .map(b -> b.getGrandTotal() != null ? b.getGrandTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Month expenses
        List<Expense> monthExpenses =
                expenseRepository.findBetween(
                        LocalDate.now().withDayOfMonth(1),
                        LocalDate.now());

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

        BigDecimal netProfit =
                monthRevenue.subtract(monthCogs).subtract(opex);

        // Alerts
        long lowStockCount = productRepository
                .findLowStockProducts().size();

        long expiringCount = batchRepository
                .findExpiringBefore(
                        LocalDate.now().plusDays(7)).size();

        long inactiveCount = customerRepository
                .findInactiveCustomers(
                        LocalDateTime.now().minusDays(30)).size();

        long pendingDeliveries = deliveryRepository
                .findByStatus(DeliveryStatus.PENDING).size();

        // Low stock items list
        List<DashboardResponse.LowStockAlert> alerts =
                productRepository.findLowStockProducts()
                        .stream()
                        .map(p -> {
                            var stock = stockRepository
                                    .findByProductId(p.getId())
                                    .orElse(null);
                            int current = stock != null
                                    ? stock.getTotalSecondaryUnits(): 0;
                            return DashboardResponse.LowStockAlert
                                    .builder()
                                    .productName(p.getName())
                                    .brand(p.getBrand())
                                    .category(p.getCategory().name())
                                    .currentStock(current)
                                    .threshold(p.getLowStockAlert())
                                    .unit(p.getSecondaryUnit())
                                    .build();
                        })
                        .collect(Collectors.toList());

        // Expiring batches list (lookahead extended to 30 days for monthly forecasting)
        List<DashboardResponse.ExpiringBatchAlert> expiringBatches = batchRepository
                .findExpiringBefore(LocalDate.now().plusDays(30))
                .stream()
                .map(b -> DashboardResponse.ExpiringBatchAlert.builder()
                        .productName(b.getProduct().getName())
                        .batchNo(b.getBatchNumber())
                        .expiryDate(b.getExpiryDate())
                        .stockCount(b.getSecondaryRemaining())
                        .build())
                .collect(Collectors.toList());

        // Inactive customers list (cutoff extended to 15 days for warning detection)
        List<DashboardResponse.InactiveCustomerAlert> inactiveCustomers = customerRepository
                .findInactiveCustomers(LocalDateTime.now().minusDays(15))
                .stream()
                .map(c -> DashboardResponse.InactiveCustomerAlert.builder()
                        .customerName(c.getName())
                        .shopName(c.getShopName())
                        .phone(c.getPhone())
                        .lastOrderDate(c.getLastOrderAt())
                        .build())
                .collect(Collectors.toList());

        // Pending deliveries list
        List<DashboardResponse.PendingDeliveryAlert> pendingDeliveriesList = deliveryRepository
                .findByStatus(DeliveryStatus.PENDING)
                .stream()
                .map(d -> DashboardResponse.PendingDeliveryAlert.builder()
                        .billNumber(d.getBill() != null ? d.getBill().getBillNumber() : null)
                        .customerName(d.getBill() != null && d.getBill().getCustomer() != null ? d.getBill().getCustomer().getName() : null)
                        .shopName(d.getBill() != null && d.getBill().getCustomer() != null ? d.getBill().getCustomer().getShopName() : null)
                        .amount(d.getBill() != null ? d.getBill().getGrandTotal() : null)
                        .build())
                .collect(Collectors.toList());

        // 1. Overdue Udhar calculation
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

        // 2. Credit Limit Exceeded calculation
        List<Customer> activeCustomers = customerRepository.findByActiveTrue();
        List<DashboardResponse.CreditLimitAlert> creditLimitAlerts = activeCustomers.stream()
                .filter(c -> {
                    BigDecimal limit = customerService.calculateEffectiveCreditLimit(c);
                    return c.getTotalPending() != null && c.getTotalPending().compareTo(limit) > 0;
                })
                .map(c -> DashboardResponse.CreditLimitAlert.builder()
                        .customerName(c.getName())
                        .shopName(c.getShopName())
                        .totalPending(c.getTotalPending())
                        .creditLimit(customerService.calculateEffectiveCreditLimit(c))
                        .build())
                .sorted((a, b) -> b.getTotalPending().subtract(b.getCreditLimit()).compareTo(a.getTotalPending().subtract(a.getCreditLimit())))
                .collect(Collectors.toList());

        long creditLimitExceededCount = creditLimitAlerts.size();

        // 3. Backup Status calculation
        Boolean backupStale = false;
        LocalDateTime lastBackupTime = backupService.getLastRunTime();
        if (lastBackupTime == null) {
            backupStale = true;
        } else {
            backupStale = lastBackupTime.isBefore(LocalDateTime.now().minusHours(25));
        }

        // Yesterday stats
        LocalDateTime yesterdayStart = start.minusDays(1);
        LocalDateTime yesterdayEnd = start;
        List<Bill> allYesterdayBills = billRepository.findBillsBetween(yesterdayStart, yesterdayEnd);
        List<Bill> yesterdayBillsList = allYesterdayBills.stream()
                .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                .collect(Collectors.toList());

        BigDecimal yesterdayRevenue = yesterdayBillsList.stream()
                .map(b -> b.getGrandTotal() != null ? b.getGrandTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CollectionBreakdown yesterdayCol = calculateCollectionBreakdown(yesterdayBillsList, yesterdayStart, yesterdayEnd);
        BigDecimal yesterdayCollection = yesterdayCol.getTotalCollected();
        BigDecimal yesterdayCash = yesterdayCol.getCollectedCash();
        BigDecimal yesterdayUPI = yesterdayCol.getCollectedUpi();
        BigDecimal yesterdayUdharRecovery = yesterdayCol.getCollectedUdhar();

        BigDecimal yesterdayNewUdhar = yesterdayBillsList.stream()
                .filter(b -> b.getPaymentMode() == com.shop.modules.billing.PaymentMode.UDHAR || b.getPaymentMode() == com.shop.modules.billing.PaymentMode.PARTIAL || b.getPaymentMode() == com.shop.modules.billing.PaymentMode.COD)
                .map(b -> b.getPendingAmount() != null ? b.getPendingAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Long yesterdayBillsCount = (long) yesterdayBillsList.size();

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

        List<Customer> allCustomers = customerRepository.findAll();
        long npaCustomersCount = allCustomers.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsNpa()) && Boolean.TRUE.equals(c.getActive()))
                .count();
        BigDecimal npaCustomersAmount = allCustomers.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsNpa()) && Boolean.TRUE.equals(c.getActive()))
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
        BigDecimal totalOutstandingUdhar = allCustomers.stream()
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .map(c -> c.getTotalPending() != null ? c.getTotalPending() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalInventoryValue = calculateTotalInventoryValue();

        // Today operational expenses
        List<Expense> todayExpensesList = expenseRepository.findBetween(LocalDate.now(), LocalDate.now());
        BigDecimal todayOpExpenses = todayExpensesList.stream()
                .filter(e -> e.getCategory() != com.shop.modules.expense.ExpenseCategory.STOCK_PURCHASE && e.getCategory() != com.shop.modules.expense.ExpenseCategory.OPENING_STOCK)
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Month operational expenses
        BigDecimal monthOpExpenses = monthExpenses.stream()
                .filter(e -> e.getCategory() != com.shop.modules.expense.ExpenseCategory.STOCK_PURCHASE && e.getCategory() != com.shop.modules.expense.ExpenseCategory.OPENING_STOCK)
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Health calculations for today
        BigDecimal todayCogs = BigDecimal.ZERO;
        for (Bill bill : todayBills) {
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
                todayCogs = todayCogs.add(buyPricePerSec.multiply(BigDecimal.valueOf(totalQtyInSec)));
            }
        }
        BigDecimal todayDamageLoss = damageLogRepository.getTotalDamageLoss(start, end);
        if (todayDamageLoss == null) todayDamageLoss = BigDecimal.ZERO;

        BigDecimal netProfitMarginPct = BigDecimal.ZERO;
        if (todayRevenue.compareTo(BigDecimal.ZERO) > 0) {
            netProfitMarginPct = todayRevenue.subtract(todayCogs).subtract(todayOpExpenses).subtract(todayDamageLoss)
                    .multiply(BigDecimal.valueOf(100)).divide(todayRevenue, 2, java.math.RoundingMode.HALF_UP);
        }

        BigDecimal todayAvgBillValue = BigDecimal.ZERO;
        if (!todayBills.isEmpty()) {
            todayAvgBillValue = todayRevenue.divide(BigDecimal.valueOf(todayBills.size()), 2, java.math.RoundingMode.HALF_UP);
        }

        BigDecimal damageLossMTD = damageLogRepository.getTotalDamageLoss(monthStart, end);
        if (damageLossMTD == null) damageLossMTD = BigDecimal.ZERO;

        long newCustomersThisMonth = allCustomers.stream()
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
            codSuccessRate = BigDecimal.valueOf(successCount * 100).divide(BigDecimal.valueOf(allCodBills.size()), 2, java.math.RoundingMode.HALF_UP);
        }

        List<Payment> periodPayments = paymentRepository.findBetween(start, end);
        BigDecimal avgCollectionDays = calculateAvgCollectionDays(periodPayments);

        // Active customers period switch
        LocalDateTime yearStart = LocalDate.now().withDayOfYear(1).atStartOfDay();
        List<Bill> yearBills = billRepository.findBillsBetween(yearStart, end).stream()
                .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                .collect(Collectors.toList());

        long activeCustomersToday = todayBills.stream().map(Bill::getCustomer).filter(c -> c != null).map(Customer::getId).distinct().count();
        long activeCustomersMonth = monthBills.stream().map(Bill::getCustomer).filter(c -> c != null).map(Customer::getId).distinct().count();
        long activeCustomersYear = yearBills.stream().map(Bill::getCustomer).filter(c -> c != null).map(Customer::getId).distinct().count();

        // 7-day trend
        List<DailyTrendPoint> sevenDayTrend = getTrendData(7);

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
                .todayExpenses(todayOpExpenses)
                .monthRevenue(monthRevenue)
                .monthExpenses(monthOpExpenses)
                .monthNetProfit(netProfit)
                .lowStockCount(lowStockCount)
                .expiringBatchesCount(expiringCount)
                .inactiveCustomersCount(inactiveCount)
                .pendingDeliveriesCount(pendingDeliveries)
                .overdueUdharCount(overdueUdharCount)
                .creditLimitExceededCount(creditLimitExceededCount)
                .backupStale(backupStale)
                .lastBackupTime(lastBackupTime)
                .lowStockAlerts(alerts)
                .expiringBatches(expiringBatches)
                .inactiveCustomers(inactiveCustomers)
                .pendingDeliveries(pendingDeliveriesList)
                .overdueUdharAlerts(overdueUdharAlerts)
                .creditLimitExceededAlerts(creditLimitAlerts)
                .yesterdayRevenue(yesterdayRevenue)
                .yesterdayCollection(yesterdayCollection)
                .yesterdayBills(yesterdayBillsCount)
                .yesterdayCash(yesterdayCash)
                .yesterdayUPI(yesterdayUPI)
                .yesterdayUdharRecovery(yesterdayUdharRecovery)
                .yesterdayNewUdhar(yesterdayNewUdhar)
                .codOverdueCount(codOverdueCount)
                .npaCustomersCount(npaCustomersCount)
                .npaCustomersAmount(npaCustomersAmount)
                .oldestPendingDays(oldestPendingDays)
                .totalOutstandingUdhar(totalOutstandingUdhar)
                .totalNewUdhar(todayNewUdhar)
                .totalExpenses(monthOpExpenses)
                .todayCashCollection(todayCol.getCollectedCash())
                .todayUPICollection(todayCol.getCollectedUpi())
                .todayUdharRecovery(todayCol.getCollectedUdhar())
                .totalWaived(todayCol.getWaivedAmount())
                .netProfitMarginPct(netProfitMarginPct)
                .avgBillValue(todayAvgBillValue)
                .damageLossMTD(damageLossMTD)
                .newCustomersThisMonth(newCustomersThisMonth)
                .codSuccessRate(codSuccessRate)
                .avgCollectionDays(avgCollectionDays)
                .activeCustomersToday(activeCustomersToday)
                .activeCustomersMonth(activeCustomersMonth)
                .activeCustomersYear(activeCustomersYear)
                .sevenDayTrend(sevenDayTrend)
                .build();
    }

    public MonthlyReportResponse getMonthlyReport(
            int year, int month) {

        LocalDateTime start =
                LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1);

        List<Bill> bills =
                billRepository.findBillsBetween(start, end).stream()
                        .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                        .collect(Collectors.toList());

        BigDecimal totalRevenue = bills.stream()
                .map(b -> b.getGrandTotal() != null ? b.getGrandTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CollectionBreakdown monthCol = calculateCollectionBreakdown(bills, start, end);
        BigDecimal totalCollected = monthCol.getTotalCollected();

        BigDecimal totalPending = bills.stream()
                .map(b -> b.getPendingAmount() != null ? b.getPendingAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Expenses
        List<Expense> expenses =
                expenseRepository.findBetween(
                        LocalDate.of(year, month, 1),
                        LocalDate.of(year, month, 1)
                                .plusMonths(1).minusDays(1));

        BigDecimal totalExpenses = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> expensesByCategory =
                expenses.stream()
                        .collect(Collectors.groupingBy(
                                e -> e.getCategory().name(),
                                Collectors.reducing(
                                        BigDecimal.ZERO,
                                        Expense::getAmount,
                                        BigDecimal::add)));

        BigDecimal monthCogs = BigDecimal.ZERO;
        for (Bill bill : bills) {
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

        BigDecimal opex = expenses.stream()
                .filter(e -> e.getCategory() != com.shop.modules.expense.ExpenseCategory.STOCK_PURCHASE && e.getCategory() != com.shop.modules.expense.ExpenseCategory.OPENING_STOCK)
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netProfit =
                totalRevenue.subtract(monthCogs).subtract(opex);

        // Fetch monthly damage loss
        BigDecimal totalDamageLoss = damageLogRepository.getTotalDamageLoss(start, end);
        if (totalDamageLoss == null) {
            totalDamageLoss = BigDecimal.ZERO;
        }

        // Calculate top products by quantity sold
        Map<String, Integer> topProductsByQty = new java.util.HashMap<>();
        for (Bill bill : bills) {
            if (bill.getStatus() == BillStatus.CANCELLED) {
                continue;
            }
            for (BillItem item : bill.getItems()) {
                String productName = item.getProduct().getName();
                int qty = item.getQuantity();
                topProductsByQty.put(productName, topProductsByQty.getOrDefault(productName, 0) + qty);
            }
        }

        // Last Month stats (M-1)
        LocalDateTime lastMonthStart = start.minusMonths(1);
        LocalDateTime lastMonthEnd = start;
        List<Bill> lastMonthBills = billRepository.findBillsBetween(lastMonthStart, lastMonthEnd).stream()
                .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                .collect(Collectors.toList());

        BigDecimal lastMonthRevenue = lastMonthBills.stream()
                .map(b -> b.getGrandTotal() != null ? b.getGrandTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CollectionBreakdown lastMonthCol = calculateCollectionBreakdown(lastMonthBills, lastMonthStart, lastMonthEnd);
        BigDecimal lastMonthCollection = lastMonthCol.getTotalCollected();

        List<Expense> lastMonthExpensesList = expenseRepository.findBetween(lastMonthStart.toLocalDate(), lastMonthEnd.toLocalDate().minusDays(1));
        BigDecimal lastMonthExpenses = lastMonthExpensesList.stream()
                .filter(e -> e.getCategory() != com.shop.modules.expense.ExpenseCategory.STOCK_PURCHASE && e.getCategory() != com.shop.modules.expense.ExpenseCategory.OPENING_STOCK)
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal lastMonthNewUdhar = lastMonthBills.stream()
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

        List<Customer> allCustomers = customerRepository.findAll();
        long npaCustomersCount = allCustomers.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsNpa()) && Boolean.TRUE.equals(c.getActive()))
                .count();
        BigDecimal npaCustomersAmount = allCustomers.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsNpa()) && Boolean.TRUE.equals(c.getActive()))
                .map(c -> c.getTotalPending() != null ? c.getTotalPending() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Bill> pendingBills = billRepository.findPendingBills();
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
        BigDecimal totalOutstandingUdhar = allCustomers.stream()
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .map(c -> c.getTotalPending() != null ? c.getTotalPending() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalInventoryValue = calculateTotalInventoryValue();
        long lowStockCount = productRepository.findLowStockProducts().size();

        BigDecimal totalNewUdhar = bills.stream()
                .filter(b -> b.getPaymentMode() == com.shop.modules.billing.PaymentMode.UDHAR || b.getPaymentMode() == com.shop.modules.billing.PaymentMode.PARTIAL || b.getPaymentMode() == com.shop.modules.billing.PaymentMode.COD)
                .map(b -> b.getPendingAmount() != null ? b.getPendingAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netProfitMarginPct = BigDecimal.ZERO;
        if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            netProfitMarginPct = netProfit.multiply(BigDecimal.valueOf(100)).divide(totalRevenue, 2, java.math.RoundingMode.HALF_UP);
        }

        BigDecimal avgBillValue = BigDecimal.ZERO;
        if (!bills.isEmpty()) {
            avgBillValue = totalRevenue.divide(BigDecimal.valueOf(bills.size()), 2, java.math.RoundingMode.HALF_UP);
        }

        long newCustomersThisMonth = allCustomers.stream()
                .filter(c -> c.getCreatedAt() != null && c.getCreatedAt().isAfter(start) && c.getCreatedAt().isBefore(end))
                .count();

        List<Bill> allCodBills = billRepository.findAll().stream()
                .filter(b -> b.getPaymentMode() == com.shop.modules.billing.PaymentMode.COD)
                .collect(Collectors.toList());
        BigDecimal codSuccessRate = BigDecimal.ZERO;
        if (!allCodBills.isEmpty()) {
            long successCount = allCodBills.stream()
                    .filter(b -> b.getStatus() == BillStatus.COD_COLLECTED || b.getStatus() == BillStatus.PAID)
                    .count();
            codSuccessRate = BigDecimal.valueOf(successCount * 100).divide(BigDecimal.valueOf(allCodBills.size()), 2, java.math.RoundingMode.HALF_UP);
        }

        List<Payment> periodPayments = paymentRepository.findBetween(start, end);
        BigDecimal avgCollectionDays = calculateAvgCollectionDays(periodPayments);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        List<Bill> todayBills = billRepository.findBillsBetween(todayStart, todayEnd).stream()
                .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                .collect(Collectors.toList());

        LocalDateTime yearStart = LocalDate.now().withDayOfYear(1).atStartOfDay();
        List<Bill> yearBills = billRepository.findBillsBetween(yearStart, end).stream()
                .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                .collect(Collectors.toList());

        long activeCustomersTodayCount = todayBills.stream().map(Bill::getCustomer).filter(c -> c != null).map(Customer::getId).distinct().count();
        long activeCustomersMonthCount = bills.stream().map(Bill::getCustomer).filter(c -> c != null).map(Customer::getId).distinct().count();
        long activeCustomersYearCount = yearBills.stream().map(Bill::getCustomer).filter(c -> c != null).map(Customer::getId).distinct().count();

        List<DailyTrendPoint> sevenDayTrend = getTrendData(7);

        return MonthlyReportResponse.builder()
                .year(year)
                .month(month)
                .totalRevenue(totalRevenue)
                .totalCollected(totalCollected)
                .totalCollectedCash(monthCol.getCollectedCash())
                .totalCollectedUpi(monthCol.getCollectedUpi())
                .totalCollectedUdhar(monthCol.getCollectedUdhar())
                .totalPending(totalPending)
                .totalWaived(monthCol.getWaivedAmount())
                .totalNewUdhar(totalNewUdhar)
                .totalBills((long) bills.size())
                .totalExpenses(opex)
                .expensesByCategory(expensesByCategory)
                .netProfit(netProfit)
                .totalDamageLoss(totalDamageLoss)
                .topProductsByQty(topProductsByQty)
                .lastMonthRevenue(lastMonthRevenue)
                .lastMonthCollection(lastMonthCollection)
                .lastMonthExpenses(lastMonthExpenses)
                .lastMonthNewUdhar(lastMonthNewUdhar)
                .totalInventoryValue(totalInventoryValue)
                .codPendingAmount(codPendingAmount)
                .codPendingBillsCount(codPendingCount)
                .codOverdueCount(codOverdueCount)
                .lowStockCount(lowStockCount)
                .npaCustomersCount(npaCustomersCount)
                .npaCustomersAmount(npaCustomersAmount)
                .oldestPendingDays(oldestPendingDays)
                .totalOutstandingUdhar(totalOutstandingUdhar)
                .netProfitMarginPct(netProfitMarginPct)
                .avgBillValue(avgBillValue)
                .damageLossMTD(totalDamageLoss)
                .newCustomersThisMonth(newCustomersThisMonth)
                .codSuccessRate(codSuccessRate)
                .avgCollectionDays(avgCollectionDays)
                .activeCustomersToday(activeCustomersTodayCount)
                .activeCustomersMonth(activeCustomersMonthCount)
                .activeCustomersYear(activeCustomersYearCount)
                .sevenDayTrend(sevenDayTrend)
                .build();
    }

    public MonthlyReportResponse getYearlyReport(int year) {
        LocalDateTime start = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime end = start.plusYears(1);

        List<Bill> bills = billRepository.findBillsBetween(start, end).stream()
                .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                .collect(Collectors.toList());

        BigDecimal totalRevenue = bills.stream()
                .map(b -> b.getGrandTotal() != null ? b.getGrandTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CollectionBreakdown yearCol = calculateCollectionBreakdown(bills, start, end);
        BigDecimal totalCollected = yearCol.getTotalCollected();

        BigDecimal totalPending = bills.stream()
                .map(b -> b.getPendingAmount() != null ? b.getPendingAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Expenses
        List<Expense> expenses = expenseRepository.findBetween(
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31));

        BigDecimal totalExpenses = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> expensesByCategory = expenses.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getCategory().name(),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                Expense::getAmount,
                                BigDecimal::add)));

        BigDecimal monthCogs = BigDecimal.ZERO;
        for (Bill bill : bills) {
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

        BigDecimal opex = expenses.stream()
                .filter(e -> e.getCategory() != com.shop.modules.expense.ExpenseCategory.STOCK_PURCHASE && e.getCategory() != com.shop.modules.expense.ExpenseCategory.OPENING_STOCK)
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netProfit = totalRevenue.subtract(monthCogs).subtract(opex);

        BigDecimal totalDamageLoss = damageLogRepository.getTotalDamageLoss(start, end);
        if (totalDamageLoss == null) {
            totalDamageLoss = BigDecimal.ZERO;
        }

        Map<String, Integer> topProductsByQty = new java.util.HashMap<>();
        for (Bill bill : bills) {
            if (bill.getStatus() == BillStatus.CANCELLED) {
                continue;
            }
            for (BillItem item : bill.getItems()) {
                String productName = item.getProduct().getName();
                int qty = item.getQuantity();
                topProductsByQty.put(productName, topProductsByQty.getOrDefault(productName, 0) + qty);
            }
        }

        // Last Year stats (Y-1)
        LocalDateTime lastYearStart = start.minusYears(1);
        LocalDateTime lastYearEnd = start;
        List<Bill> lastYearBills = billRepository.findBillsBetween(lastYearStart, lastYearEnd).stream()
                .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                .collect(Collectors.toList());

        BigDecimal lastYearRevenue = lastYearBills.stream()
                .map(b -> b.getGrandTotal() != null ? b.getGrandTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CollectionBreakdown lastYearCol = calculateCollectionBreakdown(lastYearBills, lastYearStart, lastYearEnd);
        BigDecimal lastYearCollection = lastYearCol.getTotalCollected();

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

        List<Customer> allCustomers = customerRepository.findAll();
        long npaCustomersCount = allCustomers.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsNpa()) && Boolean.TRUE.equals(c.getActive()))
                .count();
        BigDecimal npaCustomersAmount = allCustomers.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsNpa()) && Boolean.TRUE.equals(c.getActive()))
                .map(c -> c.getTotalPending() != null ? c.getTotalPending() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Bill> pendingBills = billRepository.findPendingBills();
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
        BigDecimal totalOutstandingUdhar = allCustomers.stream()
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .map(c -> c.getTotalPending() != null ? c.getTotalPending() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalInventoryValue = calculateTotalInventoryValue();
        long lowStockCount = productRepository.findLowStockProducts().size();

        BigDecimal totalNewUdhar = bills.stream()
                .filter(b -> b.getPaymentMode() == com.shop.modules.billing.PaymentMode.UDHAR || b.getPaymentMode() == com.shop.modules.billing.PaymentMode.PARTIAL || b.getPaymentMode() == com.shop.modules.billing.PaymentMode.COD)
                .map(b -> b.getPendingAmount() != null ? b.getPendingAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netProfitMarginPct = BigDecimal.ZERO;
        if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            netProfitMarginPct = netProfit.multiply(BigDecimal.valueOf(100)).divide(totalRevenue, 2, java.math.RoundingMode.HALF_UP);
        }

        BigDecimal avgBillValue = BigDecimal.ZERO;
        if (!bills.isEmpty()) {
            avgBillValue = totalRevenue.divide(BigDecimal.valueOf(bills.size()), 2, java.math.RoundingMode.HALF_UP);
        }

        long newCustomersThisMonth = allCustomers.stream()
                .filter(c -> c.getCreatedAt() != null && c.getCreatedAt().isAfter(start) && c.getCreatedAt().isBefore(end))
                .count();

        List<Bill> allCodBills = billRepository.findAll().stream()
                .filter(b -> b.getPaymentMode() == com.shop.modules.billing.PaymentMode.COD)
                .collect(Collectors.toList());
        BigDecimal codSuccessRate = BigDecimal.ZERO;
        if (!allCodBills.isEmpty()) {
            long successCount = allCodBills.stream()
                    .filter(b -> b.getStatus() == BillStatus.COD_COLLECTED || b.getStatus() == BillStatus.PAID)
                    .count();
            codSuccessRate = BigDecimal.valueOf(successCount * 100).divide(BigDecimal.valueOf(allCodBills.size()), 2, java.math.RoundingMode.HALF_UP);
        }

        List<Payment> periodPayments = paymentRepository.findBetween(start, end);
        BigDecimal avgCollectionDays = calculateAvgCollectionDays(periodPayments);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        List<Bill> todayBills = billRepository.findBillsBetween(todayStart, todayEnd).stream()
                .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                .collect(Collectors.toList());

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<Bill> monthBills = billRepository.findBillsBetween(monthStart, end).stream()
                .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                .collect(Collectors.toList());

        long activeCustomersTodayCount = todayBills.stream().map(Bill::getCustomer).filter(c -> c != null).map(Customer::getId).distinct().count();
        long activeCustomersMonthCount = monthBills.stream().map(Bill::getCustomer).filter(c -> c != null).map(Customer::getId).distinct().count();
        long activeCustomersYearCount = bills.stream().map(Bill::getCustomer).filter(c -> c != null).map(Customer::getId).distinct().count();

        List<DailyTrendPoint> sevenDayTrend = getTrendData(7);

        return MonthlyReportResponse.builder()
                .year(year)
                .month(12)
                .totalRevenue(totalRevenue)
                .totalCollected(totalCollected)
                .totalCollectedCash(yearCol.getCollectedCash())
                .totalCollectedUpi(yearCol.getCollectedUpi())
                .totalCollectedUdhar(yearCol.getCollectedUdhar())
                .totalPending(totalPending)
                .totalWaived(yearCol.getWaivedAmount())
                .totalNewUdhar(totalNewUdhar)
                .totalBills((long) bills.size())
                .totalExpenses(opex)
                .expensesByCategory(expensesByCategory)
                .netProfit(netProfit)
                .totalDamageLoss(totalDamageLoss)
                .topProductsByQty(topProductsByQty)
                .lastYearRevenue(lastYearRevenue)
                .lastYearCollection(lastYearCollection)
                .totalInventoryValue(totalInventoryValue)
                .codPendingAmount(codPendingAmount)
                .codPendingBillsCount(codPendingCount)
                .codOverdueCount(codOverdueCount)
                .lowStockCount(lowStockCount)
                .npaCustomersCount(npaCustomersCount)
                .npaCustomersAmount(npaCustomersAmount)
                .oldestPendingDays(oldestPendingDays)
                .totalOutstandingUdhar(totalOutstandingUdhar)
                .netProfitMarginPct(netProfitMarginPct)
                .avgBillValue(avgBillValue)
                .damageLossMTD(totalDamageLoss)
                .newCustomersThisMonth(newCustomersThisMonth)
                .codSuccessRate(codSuccessRate)
                .avgCollectionDays(avgCollectionDays)
                .activeCustomersToday(activeCustomersTodayCount)
                .activeCustomersMonth(activeCustomersMonthCount)
                .activeCustomersYear(activeCustomersYearCount)
                .sevenDayTrend(sevenDayTrend)
                .build();
    }

    public List<SalesmanPerformanceResponse> getSalesmenPerformance() {
        List<User> salesmen = userRepository.findByRole(UserRole.SALESMAN);
        List<SalesmanPerformanceResponse> report = new ArrayList<>();

        for (User sm : salesmen) {
            List<Area> areas = areaRepository.findBySalesmanId(sm.getId());
            List<String> areaNames = areas.stream().map(Area::getName).collect(Collectors.toList());

            // GrandTotal of CONFIRMED, PARTIAL, and PAID bills created by this salesman
            List<Bill> bills = billRepository.findByCreatedByIdAndStatusIn(
                    sm.getId(),
                    List.of(BillStatus.CONFIRMED, BillStatus.PARTIAL, BillStatus.PAID)
            );
            BigDecimal totalRevenue = bills.stream()
                    .map(b -> b.getGrandTotal() != null ? b.getGrandTotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Payments collected by this salesman (Khata payments)
            List<Payment> collections = paymentRepository.findByCollectedById(sm.getId());
            BigDecimal khataCollected = collections.stream()
                    .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Immediate payments collected by this salesman on bills they created
            BigDecimal immediateCollected = BigDecimal.ZERO;
            Map<UUID, BigDecimal> appliedAmountsMap = new java.util.HashMap<>();
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
                immediateCollected = immediateCollected.add(billPaidAmount.subtract(applied));
            }

            BigDecimal totalCollected = khataCollected.add(immediateCollected);

            // Active route credit (outstanding pending debt in assigned areas)
            BigDecimal routeCredit = BigDecimal.ZERO;
            long activeCustomers = 0;

            for (Area area : areas) {
                List<com.shop.modules.customer.Customer> customers = customerRepository.findByAreaId(area.getId());
                for (com.shop.modules.customer.Customer c : customers) {
                    if (Boolean.TRUE.equals(c.getActive())) {
                        activeCustomers++;
                        if (c.getTotalPending() != null) {
                            routeCredit = routeCredit.add(c.getTotalPending());
                        }
                    }
                }
            }

            report.add(SalesmanPerformanceResponse.builder()
                    .salesmanId(sm.getId())
                    .salesmanName(sm.getName())
                    .salesmanPhone(sm.getPhone())
                    .assignedAreas(areaNames)
                    .totalRevenueGenerated(totalRevenue)
                    .totalCollectionsMade(totalCollected)
                    .activeRouteCredit(routeCredit)
                    .activeCustomersCount(activeCustomers)
                    .build());
        }

        return report;
    }

    public DashboardSummaryResponse getDashboardSummary(int year, int month) {
        return getDashboardSummary(year, month, 5);
    }

    public DashboardSummaryResponse getDashboardSummary(int year, int month, int limit) {
        DashboardResponse today = getTodaySummary();
        MonthlyReportResponse monthly = getMonthlyReport(year, month);
        MonthlyReportResponse yearly = getYearlyReport(year);
        List<BillResponse> recentBills = billService.getRecentBills(limit);

        return DashboardSummaryResponse.builder()
                .today(today)
                .monthly(monthly)
                .yearly(yearly)
                .recentBills(recentBills)
                .build();
    }

    private CollectionBreakdown calculateCollectionBreakdown(List<Bill> bills, LocalDateTime start, LocalDateTime end) {
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
                .filter(p -> "REFUND".equalsIgnoreCase(p.getPaymentMode()))
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal immediateCash = BigDecimal.ZERO;
        BigDecimal immediateUpi = BigDecimal.ZERO;

        Map<UUID, BigDecimal> appliedAmountsMap = new java.util.HashMap<>();
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

    private BigDecimal calculateTotalInventoryValue() {
        List<Product> products = productRepository.findAll();
        BigDecimal totalValue = BigDecimal.ZERO;
        for (Product p : products) {
            List<com.shop.modules.stock.StockBatch> activeBatches = batchRepository.findByProductId(p.getId()).stream()
                    .filter(b -> b.getExhausted() == null || !b.getExhausted())
                    .collect(Collectors.toList());

            BigDecimal avgCost = BigDecimal.ZERO;
            if (activeBatches.isEmpty()) {
                avgCost = p.getBuyPricePerSecondary() != null ? p.getBuyPricePerSecondary() : BigDecimal.ZERO;
            } else {
                int totalQty = 0;
                BigDecimal totalCost = BigDecimal.ZERO;
                for (com.shop.modules.stock.StockBatch b : activeBatches) {
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
                    avgCost = totalCost.divide(BigDecimal.valueOf(totalQty), 2, java.math.RoundingMode.HALF_UP);
                }
            }

            com.shop.modules.stock.Stock stock = stockRepository.findByProductId(p.getId()).orElse(null);
            int currentStock = stock != null ? stock.getTotalSecondaryUnits() : 0;
            BigDecimal value = BigDecimal.valueOf(currentStock).multiply(avgCost);
            totalValue = totalValue.add(value);
        }
        return totalValue.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public List<DailyTrendPoint> getTrendData(int days) {
        List<DailyTrendPoint> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = start.plusDays(1);

            List<Bill> dayBills = billRepository.findBillsBetween(start, end).stream()
                    .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                    .collect(Collectors.toList());

            BigDecimal revenue = dayBills.stream()
                    .map(b -> b.getGrandTotal() != null ? b.getGrandTotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            CollectionBreakdown dayCol = calculateCollectionBreakdown(dayBills, start, end);
            BigDecimal collection = dayCol.getTotalCollected();

            BigDecimal newUdhar = dayBills.stream()
                    .filter(b -> b.getPaymentMode() == com.shop.modules.billing.PaymentMode.UDHAR || b.getPaymentMode() == com.shop.modules.billing.PaymentMode.PARTIAL || b.getPaymentMode() == com.shop.modules.billing.PaymentMode.COD)
                    .map(b -> b.getPendingAmount() != null ? b.getPendingAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String dayName = date.getDayOfWeek().name().substring(0, 3) + " " + date.getDayOfMonth();

            trend.add(DailyTrendPoint.builder()
                    .date(date)
                    .dayName(dayName)
                    .revenue(revenue)
                    .collection(collection)
                    .bills((long) dayBills.size())
                    .newUdhar(newUdhar)
                    .build());
        }
        return trend;
    }

    private BigDecimal calculateAvgCollectionDays(List<Payment> payments) {
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
        return BigDecimal.valueOf(totalDays).divide(BigDecimal.valueOf(paymentsWithBills.size()), 2, java.math.RoundingMode.HALF_UP);
    }

    public Map<String, Object> getBusinessHealth() {
        DashboardResponse today = getTodaySummary();
        Map<String, Object> health = new java.util.HashMap<>();
        health.put("netProfitMarginPct", today.getNetProfitMarginPct());
        health.put("activeCustomersToday", today.getActiveCustomersToday());
        health.put("avgBillValue", today.getAvgBillValue());
        health.put("damageLossMTD", today.getDamageLossMTD());
        health.put("npaCount", today.getNpaCustomersCount());
        health.put("npaAmount", today.getNpaCustomersAmount());
        health.put("oldestPendingDays", today.getOldestPendingDays());
        health.put("codOverdueCount", today.getCodOverdueCount());
        health.put("codSuccessRate", today.getCodSuccessRate());
        health.put("avgCollectionDays", today.getAvgCollectionDays());
        health.put("totalWaivedMTD", today.getTotalWaived());
        return health;
    }
}