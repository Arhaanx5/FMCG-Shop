package com.shop.modules.billing;

import com.shop.modules.billing.dto.BillResponse;
import com.shop.modules.billing.dto.ReturnItemsRequest;
import com.shop.modules.customer.Customer;
import com.shop.modules.damage.ClaimStatus;
import com.shop.modules.damage.DamageLog;
import com.shop.modules.damage.DamageLogRepository;
import com.shop.modules.damage.DamageReason;
import com.shop.modules.damage.UnitLevel;
import com.shop.modules.khata.Payment;
import com.shop.modules.khata.PaymentRepository;
import com.shop.modules.stock.StockBatch;
import com.shop.modules.stock.StockBatchRepository;
import com.shop.modules.stock.StockMovementService;
import com.shop.modules.stock.StockService;
import com.shop.modules.stock.dto.BatchDeductionRecord;
import com.shop.modules.user.UserRepository;
import com.shop.common.ledger.CustomerLedgerService;
import com.shop.modules.billing.validator.BillCreditValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillCancellationService {

    private final BillRepository billRepository;
    private final UserRepository userRepository;
    private final StockBatchRepository stockBatchRepository;
    private final StockService stockService;
    private final DamageLogRepository damageLogRepository;
    private final StockMovementService stockMovementService;
    private final PaymentRepository paymentRepository;
    private final CustomerLedgerService customerLedgerService;
    private final BillCalculationHelper billCalculationHelper;
    private final BillMapper billMapper;
    private final BillCreditValidator billCreditValidator;

    @Autowired(required = false)
    private com.shop.modules.delivery.DeliveryService deliveryService;

    @Transactional
    public void cancelBill(UUID id) {
        cancelBill(id, "System");
    }

    @Transactional
    public void cancelBill(UUID id, String username) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Bill not found: " + id));

        if (bill.getStatus() == BillStatus.CANCELLED) {
            throw new RuntimeException("Bill is already cancelled");
        }

        if (bill.getStatus() == BillStatus.DRAFT) {
            // Release soft reservations from batches
            for (BillItem item : bill.getItems()) {
                com.shop.modules.product.Product product = item.getProduct();
                StockBatch batch = item.getBatch();
                if (batch != null && batch.getSecondarySoftReserved() != null) {
                    int qty = item.getQuantity() + item.getFreeQuantity();
                    boolean isPrimary = item.getUnitType().name().equalsIgnoreCase(product.getPrimaryUnit());
                    int secondaryQty = isPrimary ? qty * product.getSecondaryPerPrimary() : qty;
                    int newReserved = batch.getSecondarySoftReserved() - secondaryQty;
                    batch.setSecondarySoftReserved(Math.max(0, newReserved));
                    stockBatchRepository.save(batch);
                }
            }
            bill.setStatus(BillStatus.CANCELLED);
            bill.setSubtotal(BigDecimal.ZERO);
            bill.setGstTotal(BigDecimal.ZERO);
            bill.setCessTotal(BigDecimal.ZERO);
            bill.setDiscount(BigDecimal.ZERO);
            bill.setGrandTotal(BigDecimal.ZERO);
            bill.setPaidAmount(BigDecimal.ZERO);
            bill.setPendingAmount(BigDecimal.ZERO);
            bill.setUpdatedAt(LocalDateTime.now());
            billRepository.save(bill);
            return;
        }

        // Add stock back for each item
        for (BillItem item : bill.getItems()) {
            String unitType = item.getUnitType().name();
            boolean isPrimary = unitType.equalsIgnoreCase(item.getProduct().getPrimaryUnit());
            int primaryQty = 0;
            int secondaryQty = 0;
            int totalItemQty = item.getQuantity() + item.getFreeQuantity();

            if (isPrimary) {
                primaryQty = totalItemQty;
                secondaryQty = totalItemQty * item.getProduct().getSecondaryPerPrimary();
            } else {
                secondaryQty = totalItemQty;
            }

            BigDecimal ratePerSecondary = isPrimary ? item.getRate().divide(BigDecimal.valueOf(item.getProduct().getSecondaryPerPrimary()), 4, RoundingMode.HALF_UP) : item.getRate();

            if (item.getBatchDeductions() != null && !item.getBatchDeductions().isEmpty()) {
                for (BillItemBatchDeduction deduction : item.getBatchDeductions()) {
                    int qty = deduction.getQuantityDeducted();
                    if (item.getOffer() != null && item.getOffer()) {
                        stockService.addBackOfferStock(item.getProduct().getId(), deduction.getBatch().getId(), qty, username, bill.getBillNumber(), BigDecimal.ZERO, "Cancelled bill " + bill.getBillNumber());
                    } else {
                        stockService.addBackStockToBatch(
                                item.getProduct().getId(),
                                deduction.getBatch().getId(),
                                0,
                                qty,
                                username,
                                bill.getBillNumber(),
                                ratePerSecondary,
                                "Cancelled bill " + bill.getBillNumber());
                    }
                }
            } else {
                // Fallback for legacy bills
                if (item.getOffer() != null && item.getOffer()) {
                    stockService.addBackOfferStock(item.getProduct().getId(), item.getBatch() != null ? item.getBatch().getId() : null, secondaryQty, username, bill.getBillNumber(), BigDecimal.ZERO, "Cancelled bill " + bill.getBillNumber());
                } else {
                    stockService.addBackStockToBatch(
                            item.getProduct().getId(),
                            item.getBatch() != null ? item.getBatch().getId() : null,
                            primaryQty,
                            secondaryQty,
                            username,
                            bill.getBillNumber(),
                            ratePerSecondary,
                            "Cancelled bill " + bill.getBillNumber());
                }
            }
        }

        bill.setStatus(BillStatus.CANCELLED);
        bill.setSubtotal(BigDecimal.ZERO);
        bill.setGstTotal(BigDecimal.ZERO);
        bill.setCessTotal(BigDecimal.ZERO);
        bill.setDiscount(BigDecimal.ZERO);
        bill.setGrandTotal(BigDecimal.ZERO);
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setPendingAmount(BigDecimal.ZERO);

        // Cancel linked deliveries
        if (deliveryService != null) {
            deliveryService.cancelDeliveryForBill(bill.getId(), "Linked Bill " + bill.getBillNumber() + " was cancelled");
        }

        billRepository.save(bill);
        customerLedgerService.recalculateCustomerPending(bill.getCustomer());
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse returnItems(UUID billId, ReturnItemsRequest req) {
        return returnItems(billId, req, "System");
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse returnItems(UUID billId, ReturnItemsRequest req, String username) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found: " + billId));

        if (bill.getStatus() == BillStatus.CANCELLED) {
            throw new RuntimeException("Cannot return items of a cancelled bill");
        }

        BigDecimal totalRefundAmount = BigDecimal.ZERO;
        BigDecimal totalSubtotalReduction = BigDecimal.ZERO;
        BigDecimal totalGstReduction = BigDecimal.ZERO;
        BigDecimal totalCessReduction = BigDecimal.ZERO;
        BigDecimal totalDiscountReduction = BigDecimal.ZERO;

        BigDecimal totalBeforeDiscount = bill.getSubtotal().add(bill.getGstTotal()).add(bill.getCessTotal());
        BigDecimal discountRatio = BigDecimal.ZERO;
        if (totalBeforeDiscount.compareTo(BigDecimal.ZERO) > 0 && bill.getDiscount().compareTo(BigDecimal.ZERO) > 0) {
            discountRatio = bill.getDiscount().divide(totalBeforeDiscount, 6, RoundingMode.HALF_UP);
        }

        for (ReturnItemsRequest.ReturnedItemRequest reqItem : req.getReturnedItems()) {
            BillItem item = bill.getItems().stream()
                    .filter(i -> i.getId().equals(reqItem.getBillItemId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Item not found on this bill: " + reqItem.getBillItemId()));

            if (reqItem.getQuantityToReturn() > item.getQuantity()) {
                throw new RuntimeException("Cannot return more than sold quantity (" 
                        + item.getQuantity() + ") for: " + item.getProduct().getName());
            }

            // Calculate refund amount proportionally (including GST)
            BigDecimal itemUnitTotal = item.getTotal().divide(BigDecimal.valueOf(item.getQuantity()), 4, RoundingMode.HALF_UP);
            BigDecimal refundAmount = itemUnitTotal.multiply(BigDecimal.valueOf(reqItem.getQuantityToReturn())).setScale(2, RoundingMode.HALF_UP);

            // Proportional GST reduction
            BigDecimal itemUnitGst = item.getGstAmount().divide(BigDecimal.valueOf(item.getQuantity()), 4, RoundingMode.HALF_UP);
            BigDecimal gstReduction = itemUnitGst.multiply(BigDecimal.valueOf(reqItem.getQuantityToReturn())).setScale(2, RoundingMode.HALF_UP);

            // Proportional Cess reduction
            BigDecimal itemUnitCess = item.getCessAmount().divide(BigDecimal.valueOf(item.getQuantity()), 4, RoundingMode.HALF_UP);
            BigDecimal cessReduction = itemUnitCess.multiply(BigDecimal.valueOf(reqItem.getQuantityToReturn())).setScale(2, RoundingMode.HALF_UP);

            // Proportional Subtotal reduction
            BigDecimal subtotalReduction = refundAmount.subtract(gstReduction).subtract(cessReduction);

            // Deduct proportional discount share
            BigDecimal discountShare = refundAmount.multiply(discountRatio).setScale(2, RoundingMode.HALF_UP);
            BigDecimal netRefundAmount = refundAmount.subtract(discountShare);

            totalRefundAmount = totalRefundAmount.add(netRefundAmount);
            totalSubtotalReduction = totalSubtotalReduction.add(subtotalReduction);
            totalGstReduction = totalGstReduction.add(gstReduction);
            totalCessReduction = totalCessReduction.add(cessReduction);
            totalDiscountReduction = totalDiscountReduction.add(discountShare);

            // Stock return calculations
            String unitType = item.getUnitType().name();
            boolean isPrimary = unitType.equalsIgnoreCase(item.getProduct().getPrimaryUnit());
            int primaryQty = 0;
            int secondaryQty = 0;

            if (isPrimary) {
                primaryQty = reqItem.getQuantityToReturn();
                secondaryQty = reqItem.getQuantityToReturn() * billCalculationHelper.getSafeSecondaryPerPrimary(item.getProduct());
            } else {
                secondaryQty = reqItem.getQuantityToReturn();
            }

            BigDecimal ratePerSecondary = isPrimary ? item.getRate().divide(BigDecimal.valueOf(billCalculationHelper.getSafeSecondaryPerPrimary(item.getProduct())), 4, RoundingMode.HALF_UP) : item.getRate();

            boolean isDamaged = "DAMAGED".equalsIgnoreCase(reqItem.getReturnCondition());

            if (isDamaged) {
                if (item.getBatchDeductions() != null && !item.getBatchDeductions().isEmpty()) {
                    int remainingToRestore = secondaryQty;
                    List<BillItemBatchDeduction> deductions = new ArrayList<>(item.getBatchDeductions());
                    deductions.sort((d1, d2) -> d2.getCreatedAt().compareTo(d1.getCreatedAt()));
                    
                    for (BillItemBatchDeduction deduction : deductions) {
                        if (remainingToRestore <= 0) break;
                        int deducted = deduction.getQuantityDeducted();
                        if (deducted > 0) {
                            int restoreAmt = Math.min(remainingToRestore, deducted);
                            deduction.setQuantityDeducted(deducted - restoreAmt);
                            remainingToRestore -= restoreAmt;
                            
                            StockBatch batch = deduction.getBatch();
                            int logQty = isPrimary ? restoreAmt / billCalculationHelper.getSafeSecondaryPerPrimary(item.getProduct()) : restoreAmt;
                            
                            DamageLog damageLog = DamageLog.builder()
                                    .product(item.getProduct())
                                    .batch(batch)
                                    .unitType(item.getUnitType())
                                    .unitLevel(isPrimary ? UnitLevel.PRIMARY : UnitLevel.SECONDARY)
                                    .claimStatus(ClaimStatus.CLAIMABLE)
                                    .quantity(logQty)
                                    .reason(DamageReason.OTHER)
                                    .notes("Customer return - damaged (Batch " + (batch != null ? batch.getBatchNumber() : "Unknown") + "). Bill: #" + bill.getBillNumber())
                                    .loggedAt(LocalDateTime.now())
                                    .loggedBy(userRepository.findByPhone(username).orElse(null))
                                    .valueLoss(BigDecimal.ZERO)
                                    .build();
                            damageLogRepository.save(damageLog);
                            
                            Integer batchRemaining = batch != null ? batch.getSecondaryRemaining() : 0;
                            stockMovementService.logMovement(
                                    item.getProduct(),
                                    batch,
                                    "DAMAGE",
                                    -restoreAmt,
                                    batchRemaining,
                                    batchRemaining,
                                    ratePerSecondary,
                                    username,
                                    bill.getBillNumber(),
                                    "Customer return - damaged (Batch " + (batch != null ? batch.getBatchNumber() : "Unknown") + ")"
                             );
                        }
                    }
                    item.getBatchDeductions().removeIf(d -> d.getQuantityDeducted() <= 0);
                } else {
                    StockBatch batch = item.getBatch();
                    DamageLog damageLog = DamageLog.builder()
                            .product(item.getProduct())
                            .batch(batch)
                            .unitType(item.getUnitType())
                            .unitLevel(isPrimary ? UnitLevel.PRIMARY : UnitLevel.SECONDARY)
                            .claimStatus(ClaimStatus.CLAIMABLE)
                            .quantity(reqItem.getQuantityToReturn())
                            .reason(DamageReason.OTHER)
                            .notes("Customer return - damaged. Bill: #" + bill.getBillNumber())
                            .loggedAt(LocalDateTime.now())
                            .loggedBy(userRepository.findByPhone(username).orElse(null))
                            .valueLoss(BigDecimal.ZERO)
                            .build();
                    damageLogRepository.save(damageLog);

                    Integer batchRemaining = batch != null ? batch.getSecondaryRemaining() : 0;
                    stockMovementService.logMovement(
                            item.getProduct(),
                            batch,
                            "DAMAGE",
                            -secondaryQty,
                            batchRemaining,
                            batchRemaining,
                            ratePerSecondary,
                            username,
                            bill.getBillNumber(),
                            "Customer return - damaged"
                    );
                }
            } else {
                if (item.getBatchDeductions() != null && !item.getBatchDeductions().isEmpty()) {
                    int remainingToRestore = secondaryQty;
                    List<BillItemBatchDeduction> deductions = new ArrayList<>(item.getBatchDeductions());
                    deductions.sort((d1, d2) -> d2.getCreatedAt().compareTo(d1.getCreatedAt()));

                    for (BillItemBatchDeduction deduction : deductions) {
                        if (remainingToRestore <= 0) break;
                        int deducted = deduction.getQuantityDeducted();
                        if (deducted > 0) {
                            int restoreAmt = Math.min(remainingToRestore, deducted);
                            if (item.getOffer() != null && item.getOffer()) {
                                stockService.addBackOfferStock(item.getProduct().getId(), deduction.getBatch().getId(), restoreAmt, username, bill.getBillNumber(), BigDecimal.ZERO, "Return item from bill " + bill.getBillNumber());
                            } else {
                                BigDecimal costPerSecondary = deduction.getBatch().getWeightedAvgCostSecondary();
                                stockService.addBackStockToBatch(
                                        item.getProduct().getId(),
                                        deduction.getBatch().getId(),
                                        0,
                                        restoreAmt,
                                        username,
                                        bill.getBillNumber(),
                                        costPerSecondary,
                                        "Return item from bill " + bill.getBillNumber());
                            }
                            deduction.setQuantityDeducted(deducted - restoreAmt);
                            remainingToRestore -= restoreAmt;
                        }
                    }
                    item.getBatchDeductions().removeIf(d -> d.getQuantityDeducted() <= 0);
                } else {
                    if (item.getOffer() != null && item.getOffer()) {
                        stockService.addBackOfferStock(item.getProduct().getId(), item.getBatch() != null ? item.getBatch().getId() : null, secondaryQty, username, bill.getBillNumber(), BigDecimal.ZERO, "Return item from bill " + bill.getBillNumber());
                    } else {
                        BigDecimal costPerSecondary = item.getBatch() != null ? item.getBatch().getWeightedAvgCostSecondary() : item.getProduct().getBuyPricePerSecondary();
                        stockService.addBackStockToBatch(
                                item.getProduct().getId(),
                                item.getBatch() != null ? item.getBatch().getId() : null,
                                primaryQty,
                                secondaryQty,
                                username,
                                bill.getBillNumber(),
                                costPerSecondary,
                                "Return item from bill " + bill.getBillNumber());
                    }
                }
            }

            item.setQuantity(item.getQuantity() - reqItem.getQuantityToReturn());
            item.setTotal(item.getTotal().subtract(refundAmount));
            item.setGstAmount(item.getGstAmount().subtract(gstReduction));
            item.setCessAmount(item.getCessAmount().subtract(cessReduction));

            if (item.getQuantity() <= 0) {
                item.setReturned(true);
            }
        }

        bill.setSubtotal(bill.getSubtotal().subtract(totalSubtotalReduction));
        bill.setGstTotal(bill.getGstTotal().subtract(totalGstReduction));
        bill.setCessTotal(bill.getCessTotal().subtract(totalCessReduction));
        bill.setDiscount(bill.getDiscount().subtract(totalDiscountReduction));

        BigDecimal oldPending = bill.getPendingAmount() != null ? bill.getPendingAmount() : BigDecimal.ZERO;
        BigDecimal actualRefund = totalRefundAmount.subtract(oldPending);
        if (actualRefund.compareTo(BigDecimal.ZERO) < 0) {
            actualRefund = BigDecimal.ZERO;
        }

        BigDecimal pendingReduction = totalRefundAmount.min(oldPending);
        bill.setPendingAmount(oldPending.subtract(pendingReduction));
        bill.setGrandTotal(bill.getGrandTotal().subtract(totalRefundAmount));

        if (actualRefund.compareTo(BigDecimal.ZERO) > 0) {
            String finalPaymentMode = "REFUND";
            String notesPrefix = "";
            if (req.getRefundPaymentMode() != null) {
                if ("CASH".equalsIgnoreCase(req.getRefundPaymentMode())) {
                    finalPaymentMode = "CASH";
                } else if ("UPI".equalsIgnoreCase(req.getRefundPaymentMode())) {
                    finalPaymentMode = "UPI";
                } else if ("STORE_CREDIT".equalsIgnoreCase(req.getRefundPaymentMode())) {
                    finalPaymentMode = "REFUND";
                    notesPrefix = "Store credit issued | ";
                } else if ("REFUND".equalsIgnoreCase(req.getRefundPaymentMode())) {
                    finalPaymentMode = "REFUND";
                }
            }

            Payment refundPayment = Payment.builder()
                    .customer(bill.getCustomer())
                    .bill(bill)
                    .amount(actualRefund.negate())
                    .appliedAmount(actualRefund.negate())
                    .paymentMode(finalPaymentMode)
                    .paidAt(LocalDateTime.now())
                    .notes(notesPrefix + "Return refund for Bill #" + bill.getBillNumber() + " on " + java.time.LocalDate.now())
                    .collectedBy(userRepository.findByPhone(username).orElse(null))
                    .build();
            paymentRepository.save(refundPayment);
        }

        boolean allReturned = bill.getItems().stream().allMatch(item -> item.getQuantity() <= 0);
        if (allReturned) {
            bill.setStatus(BillStatus.CANCELLED);
        }

        Bill savedBill = billRepository.save(bill);
        customerLedgerService.recalculateCustomerPending(savedBill.getCustomer());
        return billMapper.toResponse(savedBill);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse restoreBill(UUID id) {
        return restoreBill(id, "System");
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse restoreBill(UUID id, String username) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bill not found: " + id));

        if (bill.getStatus() != BillStatus.CANCELLED) {
            throw new RuntimeException("Only CANCELLED bills can be restored. Current status: " + bill.getStatus());
        }

        Customer customer = bill.getCustomer();

        billCreditValidator.validateNpaBlock(customer, bill.getPaymentMode());
        billCreditValidator.validateCreditLimit(customer, bill.getPaymentMode(), bill.getPendingAmount(), "Restoring Bill Pending");

        for (BillItem item : bill.getItems()) {
            com.shop.modules.product.Product product = item.getProduct();
            StockBatch batch = item.getBatch();

            int qty = item.getQuantity() + item.getFreeQuantity();
            boolean isPrimary = item.getUnitType().name().equalsIgnoreCase(product.getPrimaryUnit());
            int secondaryQty = isPrimary ? qty * product.getSecondaryPerPrimary() : qty;

            BigDecimal ratePerSecondary = isPrimary ? item.getRate().divide(BigDecimal.valueOf(billCalculationHelper.getSafeSecondaryPerPrimary(product)), 4, RoundingMode.HALF_UP) : item.getRate();

            UUID batchIdToDeduct = null;
            if (batch != null && batch.getSecondaryRemaining() >= secondaryQty && !batch.getExhausted() && batch.getBatchStatus() == com.shop.modules.stock.BatchStatus.ACTIVE) {
                batchIdToDeduct = batch.getId();
            }

            List<BatchDeductionRecord> depletions;
            if (isPrimary) {
                depletions = stockService.deductByPrimary(product.getId(), qty, batchIdToDeduct, username, bill.getBillNumber(), ratePerSecondary, "Restored bill " + bill.getBillNumber());
            } else {
                depletions = stockService.deductBySecondary(product.getId(), qty, batchIdToDeduct, username, bill.getBillNumber(), ratePerSecondary, "Restored bill " + bill.getBillNumber());
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

        if (bill.getPendingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            bill.setStatus(BillStatus.PAID);
        } else if (bill.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            bill.setStatus(BillStatus.PARTIAL);
        } else {
            bill.setStatus(BillStatus.CONFIRMED);
        }
        bill.setUpdatedAt(LocalDateTime.now());
        
        Bill savedBill = billRepository.save(bill);
        customerLedgerService.recalculateCustomerPending(customer);
        return billMapper.toResponse(savedBill);
    }
}
