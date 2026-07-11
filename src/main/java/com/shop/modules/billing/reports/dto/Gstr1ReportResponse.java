package com.shop.modules.billing.reports.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class Gstr1ReportResponse {

    private String taxpayerGstin;
    private String month; // YYYY-MM
    private List<B2BEntry> b2b;
    private List<B2CSEntry> b2cs;
    private List<HsnEntry> hsn;
    private DocSummary docSummary;

    @Data
    @Builder
    public static class B2BEntry {
        private String recipientGstin;
        private String receiverName;
        private String invoiceNumber;
        private String invoiceDate;
        private BigDecimal invoiceValue;
        private String pos;
        private BigDecimal rate;
        private BigDecimal taxableValue;
        private BigDecimal cgst;
        private BigDecimal sgst;
        private BigDecimal igst;
    }

    @Data
    @Builder
    public static class B2CSEntry {
        private String pos;
        private BigDecimal rate;
        private BigDecimal taxableValue;
        private BigDecimal cgst;
        private BigDecimal sgst;
        private BigDecimal igst;
    }

    @Data
    @Builder
    public static class HsnEntry {
        private String hsnSc;
        private String description;
        private String uqc;
        private BigDecimal qty;
        private BigDecimal val;
        private BigDecimal txval;
        private BigDecimal cgst;
        private BigDecimal sgst;
        private BigDecimal igst;
    }

    @Data
    @Builder
    public static class DocSummary {
        private String fromInum;
        private String toInum;
        private int totalCount;
        private int cancelledCount;
    }
}
