package com.shop.modules.dashboard.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DashboardResponse {

    private BigDecimal todayRevenue;
    private BigDecimal todayCollected;
    private BigDecimal todayCollectedCash;
    private BigDecimal todayCollectedUpi;
    private BigDecimal todayCollectedUdhar;
    private BigDecimal todayPending;
    private Long todayBills;
    private BigDecimal totalInventoryValue;
    private BigDecimal todayNewUdhar;
    private BigDecimal codPendingAmount;
    private Long codPendingBillsCount;
    private BigDecimal todayExpenses;
    private BigDecimal monthRevenue;
    private BigDecimal monthExpenses;
    private BigDecimal monthNetProfit;
    private Long lowStockCount;
    private Long expiringBatchesCount;
    private Long inactiveCustomersCount;
    private Long pendingDeliveriesCount;
    private Long overdueUdharCount;
    private Long creditLimitExceededCount;
    private Boolean backupStale;
    private LocalDateTime lastBackupTime;
    private List<LowStockAlert> lowStockAlerts;
    private List<ExpiringBatchAlert> expiringBatches;
    private List<InactiveCustomerAlert> inactiveCustomers;
    private List<PendingDeliveryAlert> pendingDeliveries;
    private List<OverdueUdharAlert> overdueUdharAlerts;
    private List<CreditLimitAlert> creditLimitExceededAlerts;

    // Yesterday comparison
    private BigDecimal yesterdayRevenue;
    private BigDecimal yesterdayCollection;
    private Long yesterdayBills;
    private BigDecimal yesterdayCash;
    private BigDecimal yesterdayUPI;
    private BigDecimal yesterdayUdharRecovery;
    private BigDecimal yesterdayNewUdhar;

    // Always current
    private Integer codOverdueCount;
    private Long npaCustomersCount;
    private BigDecimal npaCustomersAmount;
    private Long oldestPendingDays;
    private BigDecimal totalOutstandingUdhar;

    // Period / Health
    private BigDecimal totalNewUdhar;
    private BigDecimal totalExpenses;
    private BigDecimal todayCashCollection;
    private BigDecimal todayUPICollection;
    private BigDecimal todayUdharRecovery;
    private BigDecimal totalWaived;
    private BigDecimal netProfitMarginPct;
    private BigDecimal avgBillValue;
    private BigDecimal damageLossMTD;
    private Long newCustomersThisMonth;
    private BigDecimal codSuccessRate;
    private BigDecimal avgCollectionDays;
    private Long activeCustomersToday;
    private Long activeCustomersMonth;
    private Long activeCustomersYear;

    // Sparkline/trend
    private List<DailyTrendPoint> sevenDayTrend;

    @Data
    @Builder
    public static class LowStockAlert {
        private String productName;
        private String brand;
        private String category;
        private Integer currentStock;
        private Integer threshold;
        private String unit;
    }

    @Data
    @Builder
    public static class ExpiringBatchAlert {
        private String productName;
        private String batchNo;
        private LocalDate expiryDate;
        private Integer stockCount;
    }

    @Data
    @Builder
    public static class InactiveCustomerAlert {
        private String customerName;
        private String shopName;
        private String phone;
        private LocalDateTime lastOrderDate;
    }

    @Data
    @Builder
    public static class PendingDeliveryAlert {
        private String billNumber;
        private String customerName;
        private String shopName;
        private BigDecimal amount;
    }

    @Data
    @Builder
    public static class OverdueUdharAlert {
        private String customerName;
        private String shopName;
        private Integer overdueDays;
        private BigDecimal totalOverdueAmount;
    }

    @Data
    @Builder
    public static class CreditLimitAlert {
        private String customerName;
        private String shopName;
        private BigDecimal totalPending;
        private BigDecimal creditLimit;
    }
}