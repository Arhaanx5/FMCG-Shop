package com.shop.modules.billing;

import com.shop.modules.billing.dto.BillResponse;
import com.shop.modules.billing.dto.CreateBillRequest;
import com.shop.modules.customer.Customer;
import com.shop.modules.customer.CustomerRepository;
import com.shop.modules.product.Product;
import com.shop.modules.product.ProductService;
import com.shop.modules.shopprofile.ShopProfile;
import com.shop.modules.shopprofile.ShopProfileService;
import com.shop.modules.stock.StockBatch;
import com.shop.modules.stock.StockBatchRepository;
import com.shop.modules.stock.StockService;
import com.shop.modules.stock.dto.BatchDeductionRecord;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import com.shop.modules.user.UserRole;
import com.shop.modules.billing.validator.BillCreditValidator;
import com.shop.common.ledger.CustomerLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BillCreationService {

    private final BillRepository billRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final StockService stockService;
    private final ProductService productService;
    private final StockBatchRepository stockBatchRepository;
    private final ShopProfileService shopProfileService;
    private final BillCreditValidator billCreditValidator;
    private final CustomerLedgerService customerLedgerService;
    private final BillCalculationHelper billCalculationHelper;
    private final BillMapper billMapper;
    private final com.shop.modules.customer.CustomerService customerService;

    @Transactional
    public BillResponse createBill(CreateBillRequest req, String createdByPhone, boolean overrideCost) {
        Customer customer = customerService.findCustomerByIdentifier(req.getCustomerId());

        User user = userRepository.findByPhone(createdByPhone)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Validate discount for Salesman and Delivery Boy
        if (user.getRole() == UserRole.SALESMAN || user.getRole() == UserRole.DELIVERY_BOY) {
            BigDecimal discount = req.getDiscount() != null ? req.getDiscount() : BigDecimal.ZERO;
            if (discount.compareTo(BigDecimal.ZERO) > 0) {
                throw new RuntimeException("Discounts are not allowed for Salesman and Delivery Boy roles");
            }
        }

        // Prevent duplicate bill creation (within last 5 seconds)
        LocalDateTime fiveSecondsAgo = LocalDateTime.now().minusSeconds(5);
        List<Bill> recentBills = billRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId());
        for (Bill rb : recentBills) {
            if (rb.getCreatedAt() != null && rb.getCreatedAt().isAfter(fiveSecondsAgo)) {
                if (rb.getCreatedBy() != null && rb.getCreatedBy().getId().equals(user.getId())
                        && rb.getPaymentMode() == req.getPaymentMode()
                        && rb.getItems().size() == req.getItems().size()) {
                    throw new RuntimeException("Duplicate bill submission detected. Please wait 5 seconds before retrying.");
                }
            }
        }

        // Validate items not empty
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new RuntimeException("Bill must have at least one item");
        }

        // Validate PARTIAL payment
        if (req.getPaymentMode() == PaymentMode.PARTIAL
                && (req.getPaidAmount() == null || req.getPaidAmount().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new RuntimeException("Paid amount required for PARTIAL payment");
        }

        // Validate NPA customer credit block (early — before stock deduction)
        billCreditValidator.validateNpaBlock(customer, req.getPaymentMode());

        // Check if draft
        BillStatus targetStatus = req.getStatus() != null ? req.getStatus() : BillStatus.CONFIRMED;
        boolean isDraft = (targetStatus == BillStatus.DRAFT);

        // Check all stock first before creating bill
        for (CreateBillRequest.BillItemRequest itemReq : req.getItems()) {
            Product product = productService.findProductByIdentifier(itemReq.getProductId());
            billCalculationHelper.checkStockAvailability(product, itemReq, isDraft, stockService);
            billCalculationHelper.checkPriceOverrideLimits(product, itemReq, user, overrideCost);
        }

        String billNumber = billCalculationHelper.generateBillNumber(billRepository);

        ShopProfile shopProfile = shopProfileService.getActiveProfileEntity();
        if (shopProfile == null || shopProfile.getCompanyName() == null || shopProfile.getGstin() == null || shopProfile.getStateCode() == null) {
            throw new RuntimeException("Billing failed: Shop profile configuration is incomplete (missing name, GSTIN, or State Code). Please configure settings first.");
        }

        Bill bill = Bill.builder()
                .billNumber(billNumber)
                .customer(customer)
                .paymentMode(req.getPaymentMode())
                .partialPaymentMode(req.getPartialPaymentMode())
                .discount(req.getDiscount() != null ? req.getDiscount() : BigDecimal.ZERO)
                .notes(req.getNotes())
                .createdBy(user)
                .status(targetStatus)
                .items(new ArrayList<>())
                .shopName(shopProfile.getCompanyName())
                .shopGstin(shopProfile.getGstin())
                .shopFssai(shopProfile.getFssai())
                .shopStateCode(shopProfile.getStateCode())
                .isLegacySnapshot(false)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal gstTotal = BigDecimal.ZERO;
        BigDecimal cessTotal = BigDecimal.ZERO;

        for (CreateBillRequest.BillItemRequest itemReq : req.getItems()) {
            Product product = productService.findProductByIdentifier(itemReq.getProductId());

            // Get rate based on unit type (stored inclusive of tax, or overridden by customRate)
            BigDecimal inclusivePrice;
            if (itemReq.getCustomRate() != null && itemReq.getCustomRate().compareTo(BigDecimal.ZERO) >= 0) {
                inclusivePrice = itemReq.getCustomRate();
            } else {
                inclusivePrice = billCalculationHelper.getRateForUnit(product, itemReq.getUnitType().name());
            }

            BigDecimal itemGstPercent = product.getGstPercent();
            BigDecimal itemCessPercent = product.getCessPercent() != null ? product.getCessPercent() : BigDecimal.ZERO;

            // taxDivisor = 1 + (gstPercent + cessPercent) / 100
            BigDecimal taxDivisor = BigDecimal.ONE.add(
                    itemGstPercent.add(itemCessPercent).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
            );

            BigDecimal itemTotal;
            BigDecimal itemSubtotal;
            BigDecimal gstAmount;
            BigDecimal cessAmount;
            BigDecimal rate;
            BigDecimal originalRate;

            if (itemReq.isOffer()) {
                rate = BigDecimal.ZERO;
                originalRate = BigDecimal.ZERO;
                itemTotal = BigDecimal.ZERO;
                itemSubtotal = BigDecimal.ZERO;
                gstAmount = BigDecimal.ZERO;
                cessAmount = BigDecimal.ZERO;
            } else {
                // 1. Calculate line total inclusive of tax
                itemTotal = inclusivePrice.multiply(BigDecimal.valueOf(itemReq.getQuantity())).setScale(2, RoundingMode.HALF_UP);

                // 2. Back-calculate line subtotal (excluding tax)
                itemSubtotal = itemTotal.divide(taxDivisor, 2, RoundingMode.HALF_UP);

                // 3. Calculate GST and Cess at line level
                BigDecimal gstRate = itemGstPercent.divide(BigDecimal.valueOf(100));
                gstAmount = itemSubtotal.multiply(gstRate).setScale(2, RoundingMode.HALF_UP);

                BigDecimal cessRate = itemCessPercent.divide(BigDecimal.valueOf(100));
                cessAmount = itemSubtotal.multiply(cessRate).setScale(2, RoundingMode.HALF_UP);

                // 4. Adjust rounding discrepancy to match itemTotal exactly
                if (itemGstPercent.compareTo(BigDecimal.ZERO) == 0 && itemCessPercent.compareTo(BigDecimal.ZERO) == 0) {
                    gstAmount = BigDecimal.ZERO;
                    cessAmount = BigDecimal.ZERO;
                    itemSubtotal = itemTotal;
                } else {
                    BigDecimal calculatedTotal = itemSubtotal.add(gstAmount).add(cessAmount);
                    if (calculatedTotal.compareTo(itemTotal) != 0) {
                        BigDecimal diff = itemTotal.subtract(calculatedTotal);
                        gstAmount = gstAmount.add(diff);
                    }
                }

                // 5. Back-calculate the base unit rate for display / storage
                rate = itemSubtotal.divide(BigDecimal.valueOf(itemReq.getQuantity()), 4, RoundingMode.HALF_UP);

                // 6. Calculate default original base rate (excluding tax)
                BigDecimal defaultInclusivePrice = billCalculationHelper.getRateForUnit(product, itemReq.getUnitType().name());
                BigDecimal originalTotal = defaultInclusivePrice.multiply(BigDecimal.valueOf(itemReq.getQuantity())).setScale(2, RoundingMode.HALF_UP);
                BigDecimal originalSubtotal = originalTotal.divide(taxDivisor, 2, RoundingMode.HALF_UP);
                originalRate = originalSubtotal.divide(BigDecimal.valueOf(itemReq.getQuantity()), 4, RoundingMode.HALF_UP);
            }

            // Get source batch
            StockBatch linkedBatch = null;
            if (itemReq.getBatchId() != null) {
                linkedBatch = stockService.getBatchById(itemReq.getBatchId());
            } else {
                List<StockBatch> activeBatches = stockService.getBatchesByProduct(product.getId());
                if (itemReq.isOffer()) {
                    linkedBatch = activeBatches.stream()
                            .filter(b -> b.getOfferSecondaryRemaining() != null && b.getOfferSecondaryRemaining() > 0)
                            .findFirst()
                            .orElse(!activeBatches.isEmpty() ? activeBatches.get(0) : null);
                } else {
                    linkedBatch = activeBatches.stream()
                            .filter(b -> b.getSecondaryRemaining() > 0)
                            .findFirst()
                            .orElse(!activeBatches.isEmpty() ? activeBatches.get(0) : null);
                }
            }

            BillItem item = BillItem.builder()
                    .bill(bill)
                    .product(product)
                    .batch(linkedBatch)
                    .unitType(itemReq.getUnitType())
                    .quantity(itemReq.getQuantity())
                    .freeQuantity(itemReq.getFreeQuantity())
                    .rate(rate)
                    .originalRate(originalRate)
                    .gstPercent(itemReq.isOffer() ? BigDecimal.ZERO : itemGstPercent)
                    .gstAmount(gstAmount)
                    .cessPercent(itemReq.isOffer() ? BigDecimal.ZERO : itemCessPercent)
                    .cessAmount(cessAmount)
                    .total(itemTotal)
                    .offer(itemReq.isOffer())
                    .build();

            bill.getItems().add(item);
            subtotal = subtotal.add(itemSubtotal);
            gstTotal = gstTotal.add(gstAmount);
            cessTotal = cessTotal.add(cessAmount);

            // Deduct stock
            String unitType = itemReq.getUnitType().name();
            boolean isPrimary = unitType.equalsIgnoreCase(product.getPrimaryUnit());

            int totalQtyToDeduct = itemReq.getQuantity() + itemReq.getFreeQuantity();
            int secondaryQty = isPrimary ? totalQtyToDeduct * product.getSecondaryPerPrimary() : totalQtyToDeduct;

            BigDecimal ratePerSecondary = BigDecimal.ZERO;
            if (!itemReq.isOffer()) {
                if (isPrimary) {
                    ratePerSecondary = rate.divide(BigDecimal.valueOf(billCalculationHelper.getSafeSecondaryPerPrimary(product)), 4, RoundingMode.HALF_UP);
                } else {
                    ratePerSecondary = rate;
                }
            }

            if (itemReq.isOffer()) {
                if (!isDraft) {
                    stockService.deductOfferUnits(linkedBatch.getId(), secondaryQty, createdByPhone, billNumber, ratePerSecondary, "Sale of offer unit via bill " + billNumber);
                }
            } else {
                if (isDraft) {
                    if (linkedBatch != null) {
                        linkedBatch.setSecondarySoftReserved(
                                (linkedBatch.getSecondarySoftReserved() != null ? linkedBatch.getSecondarySoftReserved() : 0) + secondaryQty);
                        stockBatchRepository.save(linkedBatch);
                    }
                } else {
                    List<BatchDeductionRecord> depletions;
                    if (isPrimary) {
                        depletions = stockService.deductByPrimary(
                                product.getId(),
                                totalQtyToDeduct,
                                itemReq.getBatchId(),
                                createdByPhone,
                                billNumber,
                                ratePerSecondary,
                                "Sale via bill " + billNumber);
                    } else {
                        depletions = stockService.deductBySecondary(
                                product.getId(),
                                totalQtyToDeduct,
                                itemReq.getBatchId(),
                                createdByPhone,
                                billNumber,
                                ratePerSecondary,
                                "Sale via bill " + billNumber);
                    }

                    item.getBatchDeductions().clear();
                    for (BatchDeductionRecord depletion : depletions) {
                        StockBatch actualBatch = stockBatchRepository.findById(depletion.getBatchId())
                                .orElseThrow(() -> new RuntimeException("Batch not found: " + depletion.getBatchId()));
                        BillItemBatchDeduction deduction = BillItemBatchDeduction.builder()
                                .billItem(item)
                                .batch(actualBatch)
                                .quantityDeducted(depletion.getQuantityDeducted())
                                .build();
                        item.getBatchDeductions().add(deduction);
                    }
                }
            }
        }

        // Calculate grand total
        BigDecimal grandTotal = subtotal.add(gstTotal).add(cessTotal).subtract(bill.getDiscount());

        // Validate discount
        if (bill.getDiscount().compareTo(grandTotal) > 0) {
            throw new RuntimeException("Discount cannot exceed grand total");
        }

        bill.setSubtotal(subtotal);
        bill.setGstTotal(gstTotal);
        bill.setCessTotal(cessTotal);
        bill.setGrandTotal(grandTotal);

        // Set payment amounts
        switch (req.getPaymentMode()) {
            case UDHAR -> {
                bill.setPaidAmount(BigDecimal.ZERO);
                bill.setPendingAmount(grandTotal);
            }
            case COD -> {
                bill.setPaidAmount(BigDecimal.ZERO);
                bill.setPendingAmount(grandTotal);
            }
            case PARTIAL -> {
                BigDecimal paid = req.getPaidAmount();
                if (paid.compareTo(grandTotal) > 0) {
                    throw new RuntimeException("Paid amount cannot exceed grand total of " + grandTotal);
                }
                bill.setPaidAmount(paid);
                bill.setPendingAmount(grandTotal.subtract(paid));
            }
            default -> {
                bill.setPaidAmount(grandTotal);
                bill.setPendingAmount(BigDecimal.ZERO);
            }
        }

        if (targetStatus != BillStatus.DRAFT && targetStatus != BillStatus.CANCELLED) {
            if (req.getPaymentMode() == PaymentMode.COD) {
                bill.setStatus(BillStatus.COD_PENDING);
            } else if (bill.getPendingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                bill.setStatus(BillStatus.PAID);
            } else if (bill.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
                bill.setStatus(BillStatus.PARTIAL);
            } else {
                bill.setStatus(BillStatus.CONFIRMED);
            }
        }

        // Validate credit limit
        if (!isDraft) {
            billCreditValidator.validateCreditLimit(customer, req.getPaymentMode(), bill.getPendingAmount(), "Requested Credit");
        }

        if (!isDraft) {
            customer.setLastOrderAt(LocalDateTime.now());
            customerRepository.save(customer);
        }

        Bill savedBill = billRepository.save(bill);
        if (!isDraft) {
            customerLedgerService.recalculateCustomerPending(customer);
        }
        return billMapper.toResponse(savedBill);
    }
}
