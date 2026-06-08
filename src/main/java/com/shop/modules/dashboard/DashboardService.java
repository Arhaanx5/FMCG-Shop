package com.shop.modules.dashboard;

import com.shop.modules.billing.Bill;
import com.shop.modules.billing.BillItem;
import com.shop.modules.billing.BillRepository;
import com.shop.modules.billing.BillStatus;
import com.shop.modules.billing.BillService;
import com.shop.modules.billing.dto.BillResponse;
import com.shop.modules.customer.CustomerRepository;
import com.shop.modules.dashboard.dto.DashboardResponse;
import com.shop.modules.dashboard.dto.MonthlyReportResponse;
import com.shop.modules.dashboard.dto.DashboardSummaryResponse;
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
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
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

    @Builder
    @Data
    private static class CollectionBreakdown {
        private BigDecimal totalCollected;
        private BigDecimal collectedCash;
        private BigDecimal collectedUpi;
        private BigDecimal collectedUdhar;
    }

    public DashboardResponse getTodaySummary() {

        // Today range
        LocalDateTime start =
                LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        // Today bills (CONFIRMED, PARTIAL, and PAID are included in sales metrics)
        List<Bill> todayBills =
                billRepository.findBillsBetween(start, end).stream()
                        .filter(b -> b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL || b.getStatus() == BillStatus.PAID)
                        .collect(Collectors.toList());

        BigDecimal todayRevenue = todayBills.stream()
                .map(b -> b.getGrandTotal() != null ? b.getGrandTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CollectionBreakdown todayCol = calculateCollectionBreakdown(todayBills, start, end);
        BigDecimal todayCollected = todayCol.getTotalCollected();

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
                BigDecimal buyPricePerSec = BigDecimal.ZERO;
                if (item.getBatch() != null) {
                    buyPricePerSec = item.getBatch().getBuyPricePerSecondary(item.getProduct().getSecondaryPerPrimary());
                } else {
                    buyPricePerSec = item.getProduct().getBuyPricePerSecondary();
                }
                BigDecimal cost = buyPricePerSec.multiply(BigDecimal.valueOf(totalQty));
                monthCogs = monthCogs.add(cost);
            }
        }

        BigDecimal netProfit =
                monthRevenue.subtract(monthCogs).subtract(totalExpenses);

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

        return DashboardResponse.builder()
                .todayRevenue(todayRevenue)
                .todayCollected(todayCollected)
                .todayCollectedCash(todayCol.getCollectedCash())
                .todayCollectedUpi(todayCol.getCollectedUpi())
                .todayCollectedUdhar(todayCol.getCollectedUdhar())
                .todayPending(todayPending)
                .todayBills((long) todayBills.size())
                .monthRevenue(monthRevenue)
                .monthExpenses(totalExpenses)
                .monthNetProfit(netProfit)
                .lowStockCount(lowStockCount)
                .expiringBatchesCount(expiringCount)
                .inactiveCustomersCount(inactiveCount)
                .pendingDeliveriesCount(pendingDeliveries)
                .lowStockAlerts(alerts)
                .expiringBatches(expiringBatches)
                .inactiveCustomers(inactiveCustomers)
                .pendingDeliveries(pendingDeliveriesList)
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
                BigDecimal buyPricePerSec = BigDecimal.ZERO;
                if (item.getBatch() != null) {
                    buyPricePerSec = item.getBatch().getBuyPricePerSecondary(item.getProduct().getSecondaryPerPrimary());
                } else {
                    buyPricePerSec = item.getProduct().getBuyPricePerSecondary();
                }
                BigDecimal cost = buyPricePerSec.multiply(BigDecimal.valueOf(totalQty));
                monthCogs = monthCogs.add(cost);
            }
        }

        BigDecimal netProfit =
                totalRevenue.subtract(monthCogs).subtract(totalExpenses);

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

        return MonthlyReportResponse.builder()
                .year(year)
                .month(month)
                .totalRevenue(totalRevenue)
                .totalCollected(totalCollected)
                .totalCollectedCash(monthCol.getCollectedCash())
                .totalCollectedUpi(monthCol.getCollectedUpi())
                .totalCollectedUdhar(monthCol.getCollectedUdhar())
                .totalPending(totalPending)
                .totalBills((long) bills.size())
                .totalExpenses(totalExpenses)
                .expensesByCategory(expensesByCategory)
                .netProfit(netProfit)
                .totalDamageLoss(totalDamageLoss)
                .topProductsByQty(topProductsByQty)
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
                BigDecimal buyPricePerSec = BigDecimal.ZERO;
                if (item.getBatch() != null) {
                    buyPricePerSec = item.getBatch().getBuyPricePerSecondary(item.getProduct().getSecondaryPerPrimary());
                } else {
                    buyPricePerSec = item.getProduct().getBuyPricePerSecondary();
                }
                BigDecimal cost = buyPricePerSec.multiply(BigDecimal.valueOf(totalQty));
                monthCogs = monthCogs.add(cost);
            }
        }

        BigDecimal netProfit = totalRevenue.subtract(monthCogs).subtract(totalExpenses);

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

        return MonthlyReportResponse.builder()
                .year(year)
                .month(12)
                .totalRevenue(totalRevenue)
                .totalCollected(totalCollected)
                .totalCollectedCash(yearCol.getCollectedCash())
                .totalCollectedUpi(yearCol.getCollectedUpi())
                .totalCollectedUdhar(yearCol.getCollectedUdhar())
                .totalPending(totalPending)
                .totalBills((long) bills.size())
                .totalExpenses(totalExpenses)
                .expensesByCategory(expensesByCategory)
                .netProfit(netProfit)
                .totalDamageLoss(totalDamageLoss)
                .topProductsByQty(topProductsByQty)
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
        DashboardResponse today = getTodaySummary();
        MonthlyReportResponse monthly = getMonthlyReport(year, month);
        MonthlyReportResponse yearly = getYearlyReport(year);
        List<BillResponse> recentBills = billService.getRecentBills(5);

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
                } else {
                    immediateCash = immediateCash.add(netImmediate);
                }
            }
        }

        BigDecimal collectedCash = khataCash.add(immediateCash);
        BigDecimal collectedUpi = khataUpi.add(immediateUpi);
        BigDecimal totalCollected = collectedCash.add(collectedUpi);

        return CollectionBreakdown.builder()
                .totalCollected(totalCollected)
                .collectedCash(collectedCash)
                .collectedUpi(collectedUpi)
                .collectedUdhar(collectedUdhar)
                .build();
    }
}