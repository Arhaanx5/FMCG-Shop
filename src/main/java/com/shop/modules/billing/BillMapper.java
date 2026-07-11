package com.shop.modules.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.modules.billing.dto.BillItemResponse;
import com.shop.modules.billing.dto.BillResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
public class BillMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public BillResponse toResponse(Bill bill) {
        BigDecimal gst5 = BigDecimal.ZERO;
        BigDecimal gst12 = BigDecimal.ZERO;
        BigDecimal gst18 = BigDecimal.ZERO;
        BigDecimal gst28 = BigDecimal.ZERO;
        int totalQuantity = 0;
        List<BillItemResponse> itemResponses = new ArrayList<>();

        for (BillItem item : bill.getItems()) {
            if (item.getQuantity() > 0) {
                totalQuantity += item.getQuantity();

                int gstRate = item.getGstPercent().intValue();
                switch (gstRate) {
                    case 5  -> gst5  = gst5.add(item.getGstAmount());
                    case 12 -> gst12 = gst12.add(item.getGstAmount());
                    case 18 -> gst18 = gst18.add(item.getGstAmount());
                    case 28 -> gst28 = gst28.add(item.getGstAmount());
                }
            }

            itemResponses.add(BillItemResponse.builder()
                    .id(item.getId())
                    .productId(item.getProduct().getId())
                    .productName(item.getProduct().getName())
                    .brand(item.getProduct().getBrand())
                    .unitType(item.getUnitType())
                    .quantity(item.getQuantity())
                    .freeQuantity(item.getFreeQuantity())
                    .rate(item.getRate())
                    .originalRate(item.getOriginalRate() != null ? item.getOriginalRate() : item.getRate())
                    .gstPercent(item.getGstPercent())
                    .gstAmount(item.getGstAmount())
                    .cessPercent(item.getCessPercent())
                    .cessAmount(item.getCessAmount())
                    .total(item.getTotal())
                    .offer(item.getOffer() != null ? item.getOffer() : false)
                    .returned(item.isReturned())
                    .build());
        }

        // Build GST summary string
        StringBuilder gstSummary = new StringBuilder();
        if (gst5.compareTo(BigDecimal.ZERO) > 0)
            gstSummary.append("5%: ₹").append(gst5).append(" ");
        if (gst12.compareTo(BigDecimal.ZERO) > 0)
            gstSummary.append("12%: ₹").append(gst12).append(" ");
        if (gst18.compareTo(BigDecimal.ZERO) > 0)
            gstSummary.append("18%: ₹").append(gst18).append(" ");
        if (gst28.compareTo(BigDecimal.ZERO) > 0)
            gstSummary.append("28%: ₹").append(gst28).append(" ");

        // Build Cess summary string using TreeMap for sorted order
        Map<BigDecimal, BigDecimal> cessGroups = new TreeMap<>();
        for (BillItem item : bill.getItems()) {
            if (item.getCessPercent() != null && item.getCessPercent().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal percent = item.getCessPercent().stripTrailingZeros();
                BigDecimal amount = item.getCessAmount() != null ? item.getCessAmount() : BigDecimal.ZERO;
                cessGroups.put(percent, cessGroups.getOrDefault(percent, BigDecimal.ZERO).add(amount));
            }
        }
        StringBuilder cessSummaryBuilder = new StringBuilder();
        for (Map.Entry<BigDecimal, BigDecimal> entry : cessGroups.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                cessSummaryBuilder.append(entry.getKey()).append("%: ₹")
                        .append(entry.getValue().setScale(2, RoundingMode.HALF_UP)).append(" ");
            }
        }
        String cessSummary = cessSummaryBuilder.toString().trim();

        String areaName = bill.getCustomer().getArea() != null
                ? bill.getCustomer().getArea().getName() : null;

        return BillResponse.builder()
                .id(bill.getId())
                .billNumber(bill.getBillNumber())
                .status(bill.getStatus())
                .createdAt(bill.getCreatedAt())
                .createdBy(bill.getCreatedBy() != null ? bill.getCreatedBy().getName() : null)
                .customerId(bill.getCustomer().getId())
                .customerName(bill.getCustomer().getName())
                .customerShopName(bill.getCustomer().getShopName())
                .customerPhone(bill.getCustomer().getPhone())
                .customerArea(areaName)
                .subtotal(bill.getSubtotal())
                .gstTotal(bill.getGstTotal())
                .cessTotal(bill.getCessTotal())
                .gstSummary(gstSummary.toString().trim())
                .cessSummary(cessSummary)
                .discount(bill.getDiscount())
                .grandTotal(bill.getGrandTotal())
                .paymentMode(bill.getPaymentMode())
                .paidAmount(bill.getPaidAmount())
                .pendingAmount(bill.getPendingAmount())
                .fullyPaid(bill.getPendingAmount().compareTo(BigDecimal.ZERO) == 0)
                .items(itemResponses)
                .totalItems(itemResponses.size())
                .totalQuantity(totalQuantity)
                .version(bill.getVersion())
                .shopName(bill.getShopName())
                .shopGstin(bill.getShopGstin())
                .shopFssai(bill.getShopFssai())
                .shopStateCode(bill.getShopStateCode())
                .isLegacySnapshot(bill.getIsLegacySnapshot())
                .build();
    }

    public List<BillResponse> toResponses(List<Bill> bills) {
        return bills.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public String getBillSnapshotJson(Bill bill) {
        try {
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("billNumber", bill.getBillNumber());
            snapshot.put("customerName", bill.getCustomer().getName());
            snapshot.put("subtotal", bill.getSubtotal());
            snapshot.put("gstTotal", bill.getGstTotal());
            snapshot.put("cessTotal", bill.getCessTotal());
            snapshot.put("discount", bill.getDiscount());
            snapshot.put("grandTotal", bill.getGrandTotal());
            snapshot.put("paidAmount", bill.getPaidAmount());
            snapshot.put("pendingAmount", bill.getPendingAmount());
            snapshot.put("paymentMode", bill.getPaymentMode() != null ? bill.getPaymentMode().name() : null);
            snapshot.put("status", bill.getStatus() != null ? bill.getStatus().name() : null);

            List<Map<String, Object>> itemsList = new ArrayList<>();
            for (BillItem item : bill.getItems()) {
                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("productName", item.getProduct().getName());
                itemMap.put("quantity", item.getQuantity());
                itemMap.put("freeQuantity", item.getFreeQuantity());
                itemMap.put("rate", item.getRate());
                itemMap.put("total", item.getTotal());
                itemMap.put("offer", item.getOffer() != null && item.getOffer());
                itemMap.put("unitType", item.getUnitType() != null ? item.getUnitType().name() : null);
                itemsList.add(itemMap);
            }
            snapshot.put("items", itemsList);
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            return "{}";
        }
    }
}
