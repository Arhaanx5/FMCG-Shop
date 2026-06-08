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
    private BigDecimal monthRevenue;
    private BigDecimal monthExpenses;
    private BigDecimal monthNetProfit;
    private Long lowStockCount;
    private Long expiringBatchesCount;
    private Long inactiveCustomersCount;
    private Long pendingDeliveriesCount;
    private List<LowStockAlert> lowStockAlerts;
    private List<ExpiringBatchAlert> expiringBatches;
    private List<InactiveCustomerAlert> inactiveCustomers;
    private List<PendingDeliveryAlert> pendingDeliveries;

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
}