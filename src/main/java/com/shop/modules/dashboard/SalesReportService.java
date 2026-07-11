package com.shop.modules.dashboard;

import com.shop.modules.billing.Bill;
import com.shop.modules.billing.BillRepository;
import com.shop.modules.billing.BillStatus;
import com.shop.modules.billing.BillItem;
import com.shop.modules.customer.Customer;
import com.shop.modules.customer.CustomerRepository;
import com.shop.modules.product.ProductRepository;
import com.shop.modules.expense.Expense;
import com.shop.modules.expense.ExpenseRepository;
import com.shop.modules.khata.Payment;
import com.shop.modules.khata.PaymentRepository;
import com.shop.modules.damage.DamageLogRepository;
import com.shop.modules.dashboard.dto.MonthlyReportResponse;
import com.shop.modules.dashboard.dto.DailyTrendPoint;
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
public class SalesReportService {

    private final BillRepository billRepository;
    private final ExpenseRepository expenseRepository;
    private final DamageLogRepository damageLogRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final DashboardCalculationHelper dashboardCalculationHelper;

    public MonthlyReportResponse getMonthlyReport(int year, int month) {
        LocalDateTime start = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1);

        List<Bill> bills = billRepository.findBillsBetween(start, end).stream()
                .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                .collect(Collectors.toList());

        BigDecimal totalRevenue = bills.stream()
                .map(b -> b.getGrandTotal() != null ? b.getGrandTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CollectionBreakdown monthCol = dashboardCalculationHelper.calculateCollectionBreakdown(bills, start, end);
        BigDecimal totalCollected = monthCol.getTotalCollected();

        BigDecimal totalPending = bills.stream()
                .map(b -> b.getPendingAmount() != null ? b.getPendingAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Expenses
        List<Expense> expenses = expenseRepository.findBetween(
                LocalDate.of(year, month, 1),
                LocalDate.of(year, month, 1).plusMonths(1).minusDays(1)
        );

        BigDecimal totalExpenses = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> expensesByCategory = expenses.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getCategory().name(),
                        Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)
                ));

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

        // Fetch monthly damage loss
        BigDecimal totalDamageLoss = damageLogRepository.getTotalDamageLoss(start, end);
        if (totalDamageLoss == null) {
            totalDamageLoss = BigDecimal.ZERO;
        }

        // Calculate top products by quantity sold
        Map<String, Integer> topProductsByQty = new HashMap<>();
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

        // Last Month stats
        LocalDateTime lastMonthStart = start.minusMonths(1);
        LocalDateTime lastMonthEnd = start;
        List<Bill> lastMonthBills = billRepository.findBillsBetweenBasic(lastMonthStart, lastMonthEnd).stream()
                .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                .collect(Collectors.toList());

        BigDecimal lastMonthRevenue = lastMonthBills.stream()
                .map(b -> b.getGrandTotal() != null ? b.getGrandTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CollectionBreakdown lastMonthCol = dashboardCalculationHelper.calculateCollectionBreakdown(lastMonthBills, lastMonthStart, lastMonthEnd);
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

        BigDecimal totalInventoryValue = dashboardCalculationHelper.calculateTotalInventoryValue();
        long lowStockCount = productRepository.findLowStockProducts().size();

        BigDecimal totalNewUdhar = bills.stream()
                .filter(b -> b.getPaymentMode() == com.shop.modules.billing.PaymentMode.UDHAR || b.getPaymentMode() == com.shop.modules.billing.PaymentMode.PARTIAL || b.getPaymentMode() == com.shop.modules.billing.PaymentMode.COD)
                .map(b -> b.getPendingAmount() != null ? b.getPendingAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netProfitMarginPct = BigDecimal.ZERO;
        if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            netProfitMarginPct = netProfit.multiply(BigDecimal.valueOf(100)).divide(totalRevenue, 2, RoundingMode.HALF_UP);
        }

        BigDecimal avgBillValue = BigDecimal.ZERO;
        if (!bills.isEmpty()) {
            avgBillValue = totalRevenue.divide(BigDecimal.valueOf(bills.size()), 2, RoundingMode.HALF_UP);
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
            codSuccessRate = BigDecimal.valueOf(successCount * 100).divide(BigDecimal.valueOf(allCodBills.size()), 2, RoundingMode.HALF_UP);
        }

        List<Payment> periodPayments = paymentRepository.findBetween(start, end);
        BigDecimal avgCollectionDays = dashboardCalculationHelper.calculateAvgCollectionDays(periodPayments);

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

        CollectionBreakdown yearCol = dashboardCalculationHelper.calculateCollectionBreakdown(bills, start, end);
        BigDecimal totalCollected = yearCol.getTotalCollected();

        BigDecimal totalPending = bills.stream()
                .map(b -> b.getPendingAmount() != null ? b.getPendingAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Expense> expenses = expenseRepository.findBetween(
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31)
        );

        BigDecimal totalExpenses = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> expensesByCategory = expenses.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getCategory().name(),
                        Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)
                ));

        BigDecimal yearCogs = BigDecimal.ZERO;
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
                yearCogs = yearCogs.add(cost);
            }
        }

        BigDecimal opex = expenses.stream()
                .filter(e -> e.getCategory() != com.shop.modules.expense.ExpenseCategory.STOCK_PURCHASE && e.getCategory() != com.shop.modules.expense.ExpenseCategory.OPENING_STOCK)
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netProfit = totalRevenue.subtract(yearCogs).subtract(opex);

        BigDecimal totalDamageLoss = damageLogRepository.getTotalDamageLoss(start, end);
        if (totalDamageLoss == null) {
            totalDamageLoss = BigDecimal.ZERO;
        }

        Map<String, Integer> topProductsByQty = new HashMap<>();
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

        LocalDateTime lastYearStart = start.minusYears(1);
        LocalDateTime lastYearEnd = start;
        List<Bill> lastYearBills = billRepository.findBillsBetweenBasic(lastYearStart, lastYearEnd).stream()
                .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                .collect(Collectors.toList());

        BigDecimal lastYearRevenue = lastYearBills.stream()
                .map(b -> b.getGrandTotal() != null ? b.getGrandTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CollectionBreakdown lastYearCol = dashboardCalculationHelper.calculateCollectionBreakdown(lastYearBills, lastYearStart, lastYearEnd);
        BigDecimal lastYearCollection = lastYearCol.getTotalCollected();

        List<Expense> lastYearExpensesList = expenseRepository.findBetween(lastYearStart.toLocalDate(), lastYearEnd.toLocalDate().minusDays(1));
        BigDecimal lastYearExpenses = lastYearExpensesList.stream()
                .filter(e -> e.getCategory() != com.shop.modules.expense.ExpenseCategory.STOCK_PURCHASE && e.getCategory() != com.shop.modules.expense.ExpenseCategory.OPENING_STOCK)
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal lastYearNewUdhar = lastYearBills.stream()
                .filter(b -> b.getPaymentMode() == com.shop.modules.billing.PaymentMode.UDHAR || b.getPaymentMode() == com.shop.modules.billing.PaymentMode.PARTIAL || b.getPaymentMode() == com.shop.modules.billing.PaymentMode.COD)
                .map(b -> b.getPendingAmount() != null ? b.getPendingAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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

        BigDecimal totalInventoryValue = dashboardCalculationHelper.calculateTotalInventoryValue();
        long lowStockCount = productRepository.findLowStockProducts().size();

        BigDecimal totalNewUdhar = bills.stream()
                .filter(b -> b.getPaymentMode() == com.shop.modules.billing.PaymentMode.UDHAR || b.getPaymentMode() == com.shop.modules.billing.PaymentMode.PARTIAL || b.getPaymentMode() == com.shop.modules.billing.PaymentMode.COD)
                .map(b -> b.getPendingAmount() != null ? b.getPendingAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netProfitMarginPct = BigDecimal.ZERO;
        if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            netProfitMarginPct = netProfit.multiply(BigDecimal.valueOf(100)).divide(totalRevenue, 2, RoundingMode.HALF_UP);
        }

        BigDecimal avgBillValue = BigDecimal.ZERO;
        if (!bills.isEmpty()) {
            avgBillValue = totalRevenue.divide(BigDecimal.valueOf(bills.size()), 2, RoundingMode.HALF_UP);
        }

        long newCustomersThisYear = allCustomers.stream()
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
            codSuccessRate = BigDecimal.valueOf(successCount * 100).divide(BigDecimal.valueOf(allCodBills.size()), 2, RoundingMode.HALF_UP);
        }

        List<Payment> periodPayments = paymentRepository.findBetween(start, end);
        BigDecimal avgCollectionDays = dashboardCalculationHelper.calculateAvgCollectionDays(periodPayments);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        List<Bill> todayBills = billRepository.findBillsBetween(todayStart, todayEnd).stream()
                .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                .collect(Collectors.toList());

        long activeCustomersTodayCount = todayBills.stream().map(Bill::getCustomer).filter(c -> c != null).map(Customer::getId).distinct().count();
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
                .lastMonthRevenue(lastYearRevenue)
                .lastMonthCollection(lastYearCollection)
                .lastMonthExpenses(lastYearExpenses)
                .lastMonthNewUdhar(lastYearNewUdhar)
                .totalInventoryValue(totalInventoryValue)
                .codPendingAmount(BigDecimal.ZERO)
                .codPendingBillsCount(0L)
                .codOverdueCount(0)
                .lowStockCount(lowStockCount)
                .npaCustomersCount(npaCustomersCount)
                .npaCustomersAmount(npaCustomersAmount)
                .oldestPendingDays(oldestPendingDays)
                .totalOutstandingUdhar(totalOutstandingUdhar)
                .netProfitMarginPct(netProfitMarginPct)
                .avgBillValue(avgBillValue)
                .damageLossMTD(totalDamageLoss)
                .newCustomersThisMonth(newCustomersThisYear)
                .codSuccessRate(codSuccessRate)
                .avgCollectionDays(avgCollectionDays)
                .activeCustomersToday(activeCustomersTodayCount)
                .activeCustomersMonth(activeCustomersYearCount)
                .activeCustomersYear(activeCustomersYearCount)
                .sevenDayTrend(sevenDayTrend)
                .build();
    }

    public List<DailyTrendPoint> getTrendData(int days) {
        List<DailyTrendPoint> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        LocalDateTime overallStart = today.minusDays(days - 1).atStartOfDay();
        LocalDateTime overallEnd = today.plusDays(1).atStartOfDay();
        
        List<Bill> allBills = billRepository.findBillsBetween(overallStart, overallEnd);
        List<Payment> allPayments = paymentRepository.findBetween(overallStart, overallEnd);
        
        List<UUID> allBillIds = allBills.stream().map(Bill::getId).collect(Collectors.toList());
        List<Payment> allAppliedPayments = allBillIds.isEmpty() ? new ArrayList<>() : paymentRepository.findByBillIdIn(allBillIds);
        
        Map<UUID, List<Payment>> appliedPaymentsByBillId = allAppliedPayments.stream()
                .filter(p -> p.getBill() != null)
                .collect(Collectors.groupingBy(p -> p.getBill().getId()));

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = start.plusDays(1);

            List<Bill> dayBills = allBills.stream()
                    .filter(b -> b.getCreatedAt() != null && !b.getCreatedAt().isBefore(start) && b.getCreatedAt().isBefore(end))
                    .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                    .collect(Collectors.toList());

            BigDecimal revenue = dayBills.stream()
                    .map(b -> b.getGrandTotal() != null ? b.getGrandTotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<Payment> periodPayments = allPayments.stream()
                    .filter(p -> p.getPaidAt() != null && !p.getPaidAt().isBefore(start) && p.getPaidAt().isBefore(end))
                    .collect(Collectors.toList());

            BigDecimal khataCash = periodPayments.stream()
                    .filter(p -> "CASH".equalsIgnoreCase(p.getPaymentMode()))
                    .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal khataUpi = periodPayments.stream()
                    .filter(p -> "UPI".equalsIgnoreCase(p.getPaymentMode()))
                    .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal collectedRefunds = periodPayments.stream()
                    .filter(p -> "REFUND".equalsIgnoreCase(p.getPaymentMode()) && (p.getNotes() == null || !p.getNotes().contains("Store credit")))
                    .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal immediateCash = BigDecimal.ZERO;
            BigDecimal immediateUpi = BigDecimal.ZERO;

            for (Bill bill : dayBills) {
                BigDecimal billPaidAmount = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
                List<Payment> appliedPayments = appliedPaymentsByBillId.getOrDefault(bill.getId(), Collections.emptyList());
                BigDecimal applied = appliedPayments.stream()
                        .map(p -> p.getAppliedAmount() != null ? p.getAppliedAmount() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
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
            BigDecimal collection = collectedCash.add(collectedUpi);

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
}
