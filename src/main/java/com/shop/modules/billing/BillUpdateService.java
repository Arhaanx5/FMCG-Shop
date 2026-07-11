package com.shop.modules.billing;

import com.shop.modules.billing.dto.BillResponse;
import com.shop.modules.billing.dto.CreateBillRequest;
import com.shop.modules.customer.Customer;
import com.shop.modules.product.Product;
import com.shop.modules.product.ProductService;
import com.shop.modules.stock.StockBatch;
import com.shop.modules.stock.StockBatchRepository;
import com.shop.modules.stock.StockService;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import com.shop.common.ledger.CustomerLedgerService;
import com.shop.modules.billing.validator.BillCreditValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillUpdateService {

    private final BillRepository billRepository;
    private final UserRepository userRepository;
    private final StockBatchRepository stockBatchRepository;
    private final StockService stockService;
    private final ProductService productService;
    private final com.shop.modules.khata.PaymentRepository paymentRepository;
    private final BillEditHistoryRepository billEditHistoryRepository;
    private final CustomerLedgerService customerLedgerService;
    private final BillCalculationHelper billCalculationHelper;
    private final BillMapper billMapper;
    private final BillCreditValidator billCreditValidator;
    private final BillCancellationService billCancellationService;
    private final BillConfirmationService billConfirmationService;

    @Transactional(rollbackFor = Exception.class)
    public BillResponse updateBillDetails(UUID id, PaymentMode paymentMode, String notes, BillStatus status, BigDecimal paidAmount) {
        return updateBillDetails(id, paymentMode, notes, status, paidAmount, null, null, null, null, false, null, "System");
    }

    @Transactional(rollbackFor = Exception.class)
    public BillResponse updateBillDetails(UUID id, PaymentMode paymentMode, String notes, BillStatus status, BigDecimal paidAmount, String username) {
        return updateBillDetails(id, paymentMode, notes, status, paidAmount, null, null, null, null, false, null, username);
    }

    @Transactional(rollbackFor = Exception.class)
    public BillResponse updateBillDetails(UUID id, PaymentMode paymentMode, String notes, BillStatus status, BigDecimal paidAmount,
                                          BigDecimal discount, Integer version, String editReason,
                                          List<CreateBillRequest.BillItemRequest> newItems, String username) {
        return updateBillDetails(id, paymentMode, notes, status, paidAmount, discount, version, editReason, newItems, false, null, username);
    }

    @Transactional(rollbackFor = Exception.class)
    public BillResponse updateBillDetails(UUID id, PaymentMode paymentMode, String notes, BillStatus status, BigDecimal paidAmount,
                                          BigDecimal discount, Integer version, String editReason,
                                          List<CreateBillRequest.BillItemRequest> newItems, boolean overrideCost, String partialPaymentMode, String username) {
        if (paidAmount != null && paidAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Paid amount cannot be negative");
        }

        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bill not found: " + id));

        // Concurrency Protection
        if (version != null && !version.equals(bill.getVersion())) {
            throw new RuntimeException("This bill has been modified by another user. Please refresh and try again.");
        }

        // Validate Edit Reason
        boolean isMaterialChange = (newItems != null || discount != null);
        if (isMaterialChange && bill.getStatus() != BillStatus.DRAFT) {
            if (editReason == null || editReason.trim().isEmpty()) {
                throw new RuntimeException("Edit reason is required for modifying confirmed bills.");
            }
        }

        Customer customer = bill.getCustomer();
        User user = userRepository.findByPhone(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        // Save old snapshot
        String oldJson = billMapper.getBillSnapshotJson(bill);

        // Handle Status Change
        if (status != null && status != bill.getStatus()) {
            if (status == BillStatus.CANCELLED) {
                billCancellationService.cancelBill(id, username);
                bill = billRepository.findById(id).orElseThrow();
            } else if (status == BillStatus.CONFIRMED && bill.getStatus() == BillStatus.CANCELLED) {
                billCancellationService.restoreBill(id, username);
                bill = billRepository.findById(id).orElseThrow();
            } else if (status == BillStatus.CONFIRMED && bill.getStatus() == BillStatus.DRAFT) {
                billConfirmationService.confirmBill(id, username);
                bill = billRepository.findById(id).orElseThrow();
            }
        }

        // Stock Restoration Phase
        if (newItems != null && bill.getStatus() != BillStatus.CANCELLED) {
            for (BillItem item : bill.getItems()) {
                Product product = item.getProduct();
                StockBatch batch = item.getBatch();
                int qty = item.getQuantity() + item.getFreeQuantity();
                boolean isPrimary = item.getUnitType().name().equalsIgnoreCase(product.getPrimaryUnit());
                int secondaryQty = isPrimary ? qty * product.getSecondaryPerPrimary() : qty;

                if (bill.getStatus() == BillStatus.DRAFT) {
                    if (batch != null && batch.getSecondarySoftReserved() != null) {
                        int newReserved = batch.getSecondarySoftReserved() - secondaryQty;
                        batch.setSecondarySoftReserved(Math.max(0, newReserved));
                        stockBatchRepository.save(batch);
                    }
                } else {
                    BigDecimal ratePerSecondary = isPrimary ? item.getRate().divide(BigDecimal.valueOf(billCalculationHelper.getSafeSecondaryPerPrimary(product)), 4, RoundingMode.HALF_UP) : item.getRate();
                    if (item.getOffer() != null && item.getOffer()) {
                        stockService.addBackOfferStock(product.getId(), batch.getId(), secondaryQty, username, bill.getBillNumber(), BigDecimal.ZERO, "Edit bill restoration " + bill.getBillNumber());
                    } else {
                        stockService.addBackStockToBatch(
                                product.getId(),
                                batch != null ? batch.getId() : null,
                                isPrimary ? qty : 0,
                                secondaryQty,
                                username,
                                bill.getBillNumber(),
                                ratePerSecondary,
                                "Edit bill restoration " + bill.getBillNumber());
                    }
                }
            }
            bill.getItems().clear();
            billRepository.saveAndFlush(bill);
        }

        // Recalculation & Stock Validation/Deduction
        boolean isDraft = (status != null ? status == BillStatus.DRAFT : bill.getStatus() == BillStatus.DRAFT);
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal gstTotal = BigDecimal.ZERO;
        BigDecimal cessTotal = BigDecimal.ZERO;

        if (newItems != null && bill.getStatus() != BillStatus.CANCELLED) {
            for (CreateBillRequest.BillItemRequest itemReq : newItems) {
                Product product = productService.findProductByIdentifier(itemReq.getProductId());
                billCalculationHelper.checkStockAvailability(product, itemReq, isDraft, stockService);
                billCalculationHelper.checkPriceOverrideLimits(product, itemReq, user, overrideCost);
            }

            for (CreateBillRequest.BillItemRequest itemReq : newItems) {
                Product product = productService.findProductByIdentifier(itemReq.getProductId());
                boolean isPrimary = itemReq.getUnitType().name().equalsIgnoreCase(product.getPrimaryUnit());

                BigDecimal inclusivePrice;
                if (itemReq.getCustomRate() != null && itemReq.getCustomRate().compareTo(BigDecimal.ZERO) >= 0) {
                    inclusivePrice = itemReq.getCustomRate();
                } else {
                    inclusivePrice = billCalculationHelper.getRateForUnit(product, itemReq.getUnitType().name());
                }

                BigDecimal itemGstPercent = product.getGstPercent();
                BigDecimal itemCessPercent = product.getCessPercent() != null ? product.getCessPercent() : BigDecimal.ZERO;
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
                    itemTotal = inclusivePrice.multiply(BigDecimal.valueOf(itemReq.getQuantity())).setScale(2, RoundingMode.HALF_UP);
                    itemSubtotal = itemTotal.divide(taxDivisor, 2, RoundingMode.HALF_UP);
                    
                    BigDecimal gstRate = itemGstPercent.divide(BigDecimal.valueOf(100));
                    gstAmount = itemSubtotal.multiply(gstRate).setScale(2, RoundingMode.HALF_UP);

                    BigDecimal cessRate = itemCessPercent.divide(BigDecimal.valueOf(100));
                    cessAmount = itemSubtotal.multiply(cessRate).setScale(2, RoundingMode.HALF_UP);

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

                    rate = itemSubtotal.divide(BigDecimal.valueOf(itemReq.getQuantity()), 4, RoundingMode.HALF_UP);
                    
                    BigDecimal defaultInclusivePrice = billCalculationHelper.getRateForUnit(product, itemReq.getUnitType().name());
                    BigDecimal originalTotal = defaultInclusivePrice.multiply(BigDecimal.valueOf(itemReq.getQuantity())).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal originalSubtotal = originalTotal.divide(taxDivisor, 2, RoundingMode.HALF_UP);
                    originalRate = originalSubtotal.divide(BigDecimal.valueOf(itemReq.getQuantity()), 4, RoundingMode.HALF_UP);
                }

                // Sourced Batch
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

                int totalQtyToDeduct = itemReq.getQuantity() + itemReq.getFreeQuantity();
                int secondaryQty = isPrimary ? totalQtyToDeduct * product.getSecondaryPerPrimary() : totalQtyToDeduct;

                BigDecimal ratePerSecondary = BigDecimal.ZERO;
                if (!itemReq.isOffer()) {
                    ratePerSecondary = isPrimary ? rate.divide(BigDecimal.valueOf(billCalculationHelper.getSafeSecondaryPerPrimary(product)), 4, RoundingMode.HALF_UP) : rate;
                }

                List<com.shop.modules.stock.dto.BatchDeductionRecord> depletions;
                if (itemReq.isOffer()) {
                    if (!isDraft) {
                        stockService.deductOfferUnits(linkedBatch.getId(), secondaryQty, username, bill.getBillNumber(), ratePerSecondary, "Sale of offer unit via bill edit " + bill.getBillNumber());
                    }
                    depletions = new ArrayList<>();
                } else {
                    if (isDraft) {
                        if (linkedBatch != null) {
                            linkedBatch.setSecondarySoftReserved(
                                    (linkedBatch.getSecondarySoftReserved() != null ? linkedBatch.getSecondarySoftReserved() : 0) + secondaryQty);
                            stockBatchRepository.save(linkedBatch);
                        }
                        depletions = new ArrayList<>();
                    } else {
                        if (isPrimary) {
                            depletions = stockService.deductByPrimary(product.getId(), totalQtyToDeduct, linkedBatch.getId(), username, bill.getBillNumber(), ratePerSecondary, "Sale via bill edit " + bill.getBillNumber());
                        } else {
                            depletions = stockService.deductBySecondary(product.getId(), totalQtyToDeduct, linkedBatch.getId(), username, bill.getBillNumber(), ratePerSecondary, "Sale via bill edit " + bill.getBillNumber());
                        }
                    }
                }

                item.getBatchDeductions().clear();
                for (com.shop.modules.stock.dto.BatchDeductionRecord depletion : depletions) {
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

            bill.setSubtotal(subtotal);
            bill.setGstTotal(gstTotal);
            bill.setCessTotal(cessTotal);
        }

        // Discount & Grand Total Recalculation
        if (discount != null) {
            bill.setDiscount(discount);
        }
        
        BigDecimal computedGrandTotal = bill.getSubtotal().add(bill.getGstTotal()).add(bill.getCessTotal()).subtract(bill.getDiscount());

        if (bill.getDiscount().compareTo(computedGrandTotal.add(bill.getDiscount())) > 0) {
            throw new RuntimeException("Discount cannot exceed subtotal with taxes");
        }
        bill.setGrandTotal(computedGrandTotal);

        // Payment Reconciliation
        if (bill.getStatus() != BillStatus.CANCELLED) {
            bill.setForceStatusChange(true);
            BigDecimal oldPending = bill.getPendingAmount();
            BigDecimal newPending;

            if (paymentMode != null) {
                bill.setPaymentMode(paymentMode);
            }
            if (partialPaymentMode != null) {
                bill.setPartialPaymentMode(partialPaymentMode);
            }

            BigDecimal totalPaymentsApplied = paymentRepository.findByBillIdIn(List.of(bill.getId()))
                    .stream()
                    .map(p -> p.getAppliedAmount() != null ? p.getAppliedAmount() : p.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (bill.getPaymentMode() == PaymentMode.UDHAR) {
                bill.setPaidAmount(BigDecimal.ZERO);
            } else if (bill.getPaymentMode() == PaymentMode.COD) {
                bill.setPaidAmount(BigDecimal.ZERO);
            } else if (bill.getPaymentMode() == PaymentMode.PARTIAL) {
                BigDecimal paid = paidAmount != null ? paidAmount : bill.getPaidAmount();
                if (paid.compareTo(bill.getGrandTotal()) > 0) {
                    paid = bill.getGrandTotal();
                }
                bill.setPaidAmount(paid);
            } else {
                bill.setPaidAmount(bill.getGrandTotal());
            }

            BigDecimal spotPaid = bill.getPaidAmount();
            BigDecimal totalCredited = spotPaid.add(totalPaymentsApplied);
            if (totalCredited.compareTo(bill.getGrandTotal()) >= 0) {
                bill.setPendingAmount(BigDecimal.ZERO);
                newPending = BigDecimal.ZERO;
            } else {
                bill.setPendingAmount(bill.getGrandTotal().subtract(totalCredited));
                newPending = bill.getGrandTotal().subtract(totalCredited);
            }

            // Update customer pending based on outstanding changes & check limits
            if (newPending.compareTo(oldPending) != 0) {
                BigDecimal diff = newPending.subtract(oldPending);
                billCreditValidator.validateForUpdate(customer, diff);
            }

            // Set final status dynamically
            if (bill.getStatus() != BillStatus.CANCELLED && bill.getStatus() != BillStatus.DRAFT) {
                if (bill.getPaymentMode() == PaymentMode.COD) {
                    if (bill.getPaidAmount().compareTo(BigDecimal.ZERO) == 0) {
                        bill.setStatus(BillStatus.COD_PENDING);
                    } else if (bill.getPendingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                        bill.setStatus(BillStatus.COD_COLLECTED);
                    } else {
                        bill.setStatus(BillStatus.PARTIAL);
                    }
                } else {
                    if (bill.getPendingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                        bill.setStatus(BillStatus.PAID);
                    } else if (bill.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
                        bill.setStatus(BillStatus.PARTIAL);
                    } else {
                        bill.setStatus(BillStatus.CONFIRMED);
                    }
                }
            }
        }

        if (notes != null) {
            bill.setNotes(notes);
        }

        Bill savedBill = billRepository.save(bill);
        customerLedgerService.recalculateCustomerPending(customer);

        // Capture Audit Log
        String newJson = billMapper.getBillSnapshotJson(savedBill);
        if (isMaterialChange) {
            BillEditHistory history = BillEditHistory.builder()
                    .billId(savedBill.getId())
                    .billNumber(savedBill.getBillNumber())
                    .editedBy(username)
                    .editedAt(LocalDateTime.now())
                    .oldJson(oldJson)
                    .newJson(newJson)
                    .reason(editReason != null ? editReason : "Updated bill details")
                    .build();
            billEditHistoryRepository.save(history);
        }

        return billMapper.toResponse(savedBill);
    }
}
