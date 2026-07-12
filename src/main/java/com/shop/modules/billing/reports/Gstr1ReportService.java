package com.shop.modules.billing.reports;

import com.shop.modules.billing.Bill;
import com.shop.modules.billing.BillItem;
import com.shop.modules.billing.BillRepository;
import com.shop.modules.billing.BillStatus;
import com.shop.modules.billing.reports.dto.Gstr1ReportResponse;
import com.shop.modules.product.Product;
import com.shop.modules.shopprofile.ShopProfile;
import com.shop.modules.shopprofile.ShopProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class Gstr1ReportService {

    private final BillRepository billRepository;
    private final ShopProfileService shopProfileService;

    public Gstr1ReportResponse generateGstr1Report(String monthStr) {
        // Parse month (YYYY-MM)
        YearMonth ym;
        try {
            ym = YearMonth.parse(monthStr.trim());
        } catch (Exception e) {
            throw new RuntimeException("Invalid month format. Please use YYYY-MM (e.g. 2026-07)");
        }

        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.atEndOfMonth().atTime(23, 59, 59, 999999999);

        ShopProfile shopProfile = shopProfileService.getActiveProfileEntity();

        // Fetch all bills in the date range
        List<Bill> bills = billRepository.findBillsBetween(start, end);

        // 1. Missing HSN Blocking Validation Check
        // Include PAID, PARTIAL, and CONFIRMED bills (exclude DRAFT and CANCELLED)
        List<Bill> confirmedBills = bills.stream()
                .filter(b -> b.getStatus() == BillStatus.PAID
                        || b.getStatus() == BillStatus.PARTIAL
                        || b.getStatus() == BillStatus.CONFIRMED)
                .collect(Collectors.toList());

        List<String> missingHsnProducts = new ArrayList<>();
        for (Bill bill : confirmedBills) {
            for (BillItem item : bill.getItems()) {
                Product p = item.getProduct();
                if (p != null && (p.getHsnCode() == null || p.getHsnCode().isBlank())) {
                    if (!missingHsnProducts.contains(p.getName())) {
                        missingHsnProducts.add(p.getName());
                    }
                }
            }
        }

        if (!missingHsnProducts.isEmpty()) {
            throw new RuntimeException("GSTR-1 Export Blocked: The following products sold in " + monthStr + " are missing HSN codes: " 
                    + String.join(", ", missingHsnProducts) + ". Please update their HSN codes first.");
        }

        // 2. Build B2C Small (B2CS) consolidated map
        // Group by: Place of Supply (POS) + Tax Rate
        // Since currently customer table has no GSTIN, all customers are B2C unregistered.
        // POS defaults to Shop State Code (e.g. "09" for UP).
        Map<String, Map<BigDecimal, B2CSAccumulator>> b2csGroups = new HashMap<>();

        for (Bill bill : confirmedBills) {
            String pos = shopProfile.getStateCode(); // Default to shop state
            
            for (BillItem item : bill.getItems()) {
                BigDecimal rate = item.getGstPercent();
                BigDecimal totalVal = item.getTotal() != null ? item.getTotal() : BigDecimal.ZERO;
                BigDecimal gstAmt = item.getGstAmount() != null ? item.getGstAmount() : BigDecimal.ZERO;
                BigDecimal cessAmt = item.getCessAmount() != null ? item.getCessAmount() : BigDecimal.ZERO;
                BigDecimal txval = totalVal.subtract(gstAmt).subtract(cessAmt);

                b2csGroups.computeIfAbsent(pos, k -> new HashMap<>());
                b2csGroups.get(pos).computeIfAbsent(rate, r -> new B2CSAccumulator());

                B2CSAccumulator acc = b2csGroups.get(pos).get(rate);
                acc.taxableValue = acc.taxableValue.add(txval);
                acc.gstTotal = acc.gstTotal.add(gstAmt);
            }
        }

        List<Gstr1ReportResponse.B2CSEntry> b2csList = new ArrayList<>();
        for (Map.Entry<String, Map<BigDecimal, B2CSAccumulator>> posEntry : b2csGroups.entrySet()) {
            String pos = posEntry.getKey();
            for (Map.Entry<BigDecimal, B2CSAccumulator> rateEntry : posEntry.getValue().entrySet()) {
                BigDecimal rate = rateEntry.getKey();
                B2CSAccumulator acc = rateEntry.getValue();

                // Splits
                BigDecimal cgst = BigDecimal.ZERO;
                BigDecimal sgst = BigDecimal.ZERO;
                BigDecimal igst = BigDecimal.ZERO;

                if (pos.equals(shopProfile.getStateCode())) {
                    cgst = acc.gstTotal.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                    sgst = acc.gstTotal.subtract(cgst);
                } else {
                    igst = acc.gstTotal;
                }

                b2csList.add(Gstr1ReportResponse.B2CSEntry.builder()
                        .pos(pos)
                        .rate(rate)
                        .taxableValue(acc.taxableValue.setScale(2, RoundingMode.HALF_UP))
                        .cgst(cgst)
                        .sgst(sgst)
                        .igst(igst)
                        .build());
            }
        }

        // 3. Build HSN Summary Map
        // Group by HSN Code + Description + Rate
        Map<String, HsnAccumulator> hsnGroups = new HashMap<>();
        for (Bill bill : confirmedBills) {
            for (BillItem item : bill.getItems()) {
                Product p = item.getProduct();
                if (p == null) continue;

                String hsn = p.getHsnCode();
                String key = hsn + "_" + item.getGstPercent().toString();

                hsnGroups.computeIfAbsent(key, k -> new HsnAccumulator(hsn, p.getName(), p.getPrimaryUnit()));

                HsnAccumulator acc = hsnGroups.get(key);
                acc.quantity = acc.quantity.add(BigDecimal.valueOf(item.getQuantity()));
                acc.totalValue = acc.totalValue.add(item.getTotal());
                BigDecimal itemTotalVal = item.getTotal() != null ? item.getTotal() : BigDecimal.ZERO;
                BigDecimal itemGstAmt = item.getGstAmount() != null ? item.getGstAmount() : BigDecimal.ZERO;
                BigDecimal itemCessAmt = item.getCessAmount() != null ? item.getCessAmount() : BigDecimal.ZERO;
                BigDecimal itemTxval = itemTotalVal.subtract(itemGstAmt).subtract(itemCessAmt);
                acc.taxableValue = acc.taxableValue.add(itemTxval);
                acc.gstTotal = acc.gstTotal.add(item.getGstAmount());
                acc.rate = item.getGstPercent();
            }
        }

        List<Gstr1ReportResponse.HsnEntry> hsnList = new ArrayList<>();
        for (HsnAccumulator acc : hsnGroups.values()) {
            BigDecimal cgst = BigDecimal.ZERO;
            BigDecimal sgst = BigDecimal.ZERO;
            BigDecimal igst = BigDecimal.ZERO;

            // Assuming intra-state for local business
            cgst = acc.gstTotal.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            sgst = acc.gstTotal.subtract(cgst);

            hsnList.add(Gstr1ReportResponse.HsnEntry.builder()
                    .hsnSc(acc.hsn)
                    .description(acc.description)
                    .uqc(acc.uom != null ? acc.uom : "BOX")
                    .qty(acc.quantity.setScale(2, RoundingMode.HALF_UP))
                    .val(acc.totalValue.setScale(2, RoundingMode.HALF_UP))
                    .txval(acc.taxableValue.setScale(2, RoundingMode.HALF_UP))
                    .cgst(cgst)
                    .sgst(sgst)
                    .igst(igst)
                    .build());
        }

        // 4. Build Document Summary
        String fromInum = "";
        String toInum = "";
        int totalCount = confirmedBills.size();
        int cancelledCount = 0;

        if (!bills.isEmpty()) {
            fromInum = bills.get(0).getBillNumber();
            toInum = bills.get(bills.size() - 1).getBillNumber();
            cancelledCount = (int) bills.stream()
                    .filter(b -> b.getStatus() == BillStatus.CANCELLED)
                    .count();
        }

        Gstr1ReportResponse.DocSummary docSummary = Gstr1ReportResponse.DocSummary.builder()
                .fromInum(fromInum)
                .toInum(toInum)
                .totalCount(totalCount)
                .cancelledCount(cancelledCount)
                .build();

        return Gstr1ReportResponse.builder()
                .taxpayerGstin(shopProfile.getGstin())
                .month(monthStr)
                .b2b(new ArrayList<>()) // B2B list is empty as all customers are B2C currently
                .b2cs(b2csList)
                .hsn(hsnList)
                .docSummary(docSummary)
                .build();
    }

    private static class B2CSAccumulator {
        BigDecimal taxableValue = BigDecimal.ZERO;
        BigDecimal gstTotal = BigDecimal.ZERO;
    }

    private static class HsnAccumulator {
        String hsn;
        String description;
        String uom;
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal taxableValue = BigDecimal.ZERO;
        BigDecimal gstTotal = BigDecimal.ZERO;
        BigDecimal rate = BigDecimal.ZERO;

        HsnAccumulator(String hsn, String description, String uom) {
            this.hsn = hsn;
            this.description = description;
            this.uom = uom;
        }
    }
}
