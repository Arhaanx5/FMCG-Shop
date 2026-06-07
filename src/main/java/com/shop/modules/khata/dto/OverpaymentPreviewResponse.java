package com.shop.modules.khata.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class OverpaymentPreviewResponse {

    /** Bill the payment was originally recorded against */
    private UUID sourceBillId;
    private String sourceBillNumber;
    private BigDecimal sourceBillPending;

    /** Amount paid by user */
    private BigDecimal paymentAmount;

    /** paymentAmount - sourceBillPending */
    private BigDecimal excessAmount;

    /** All OTHER pending bills for this customer (for Option 1 manual select) */
    private List<BillSummary> otherPendingBills;

    /** Pre-computed FIFO distribution (for Option 2 auto preview) */
    private List<AutoDistributionEntry> autoDistribution;

    /** Amount left after FIFO exhausts all pending bills (would block if > 0) */
    private BigDecimal remainingAfterAuto;

    // ── Nested types ──

    @Data
    @Builder
    public static class BillSummary {
        private UUID billId;
        private String billNumber;
        private BigDecimal pendingAmount;
        private BigDecimal grandTotal;
        private String createdAt;
    }

    @Data
    @Builder
    public static class AutoDistributionEntry {
        private UUID billId;
        private String billNumber;
        private BigDecimal pendingBefore;
        private BigDecimal amountApplied;
        private BigDecimal pendingAfter;
        private boolean willBeFullyPaid;
    }
}
