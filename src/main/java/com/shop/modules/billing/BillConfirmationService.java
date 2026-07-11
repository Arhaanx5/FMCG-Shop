package com.shop.modules.billing;

import com.shop.modules.billing.dto.BillResponse;
import com.shop.modules.billing.dto.CreateBillRequest;
import com.shop.modules.customer.Customer;
import com.shop.modules.customer.CustomerRepository;
import com.shop.modules.product.Product;
import com.shop.modules.stock.StockBatch;
import com.shop.modules.stock.StockBatchRepository;
import com.shop.modules.stock.StockService;
import com.shop.modules.stock.dto.BatchDeductionRecord;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import com.shop.common.ledger.CustomerLedgerService;
import com.shop.modules.billing.validator.BillCreditValidator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillConfirmationService {

    private final BillRepository billRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final StockBatchRepository stockBatchRepository;
    private final StockService stockService;
    private final CustomerLedgerService customerLedgerService;
    private final BillCalculationHelper billCalculationHelper;
    private final BillMapper billMapper;
    private final BillCreditValidator billCreditValidator;

    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse confirmBill(UUID billId) {
        return confirmBill(billId, false, "System");
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse confirmBill(UUID billId, String username) {
        return confirmBill(billId, false, username);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse confirmBill(UUID billId, boolean overrideCost, String username) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found: " + billId));

        if (bill.getStatus() != BillStatus.DRAFT) {
            throw new RuntimeException("Only DRAFT bills can be confirmed. Current status: " + bill.getStatus());
        }

        Customer customer = bill.getCustomer();
        User user = userRepository.findByPhone(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        // Validate NPA block + credit limit
        billCreditValidator.validateNpaBlock(customer, bill.getPaymentMode());
        billCreditValidator.validateCreditLimit(customer, bill.getPaymentMode(), bill.getPendingAmount(), "Requested Credit");

        // Run price override checks
        for (BillItem item : bill.getItems()) {
            CreateBillRequest.BillItemRequest itemReq = new CreateBillRequest.BillItemRequest();
            itemReq.setProductId(item.getProduct().getId().toString());
            itemReq.setQuantity(item.getQuantity());
            itemReq.setFreeQuantity(item.getFreeQuantity());
            itemReq.setUnitType(item.getUnitType());
            itemReq.setCustomRate(item.getRate());
            itemReq.setOffer(item.getOffer() != null && item.getOffer());
            billCalculationHelper.checkPriceOverrideLimits(item.getProduct(), itemReq, user, overrideCost);
        }

        // Validate and deduct stock, and release soft reservations
        for (BillItem item : bill.getItems()) {
            Product product = item.getProduct();
            StockBatch batch = item.getBatch();

            int qty = item.getQuantity() + item.getFreeQuantity();
            boolean isPrimary = item.getUnitType().name().equalsIgnoreCase(product.getPrimaryUnit());
            int secondaryQty = isPrimary ? qty * billCalculationHelper.getSafeSecondaryPerPrimary(product) : qty;

            if (batch == null) {
                throw new RuntimeException("Sourced stock batch missing for product: " + product.getName());
            }

            BigDecimal ratePerSecondary = isPrimary ? item.getRate().divide(BigDecimal.valueOf(billCalculationHelper.getSafeSecondaryPerPrimary(product)), 4, RoundingMode.HALF_UP) : item.getRate();

            if (item.getOffer() != null && item.getOffer()) {
                int available = batch.getOfferSecondaryRemaining() != null ? batch.getOfferSecondaryRemaining() : 0;
                if (available < secondaryQty) {
                    throw new RuntimeException("Insufficient offer stock in batch " + batch.getBatchNumber()
                            + " for product: " + product.getName()
                            + " | Available: " + available
                            + " | Requested: " + secondaryQty);
                }
                stockService.deductOfferUnits(batch.getId(), secondaryQty, username, bill.getBillNumber(), BigDecimal.ZERO, "Confirmed bill " + bill.getBillNumber());
                continue;
            }

            UUID targetBatchId = batch.getId();
            if (batch.getSecondaryRemaining() < secondaryQty) {
                List<StockBatch> activeBatches = stockService.getBatchesByProduct(product.getId());
                int totalAvailable = activeBatches.stream()
                        .mapToInt(StockBatch::getSecondaryRemaining)
                        .sum();
                if (totalAvailable < secondaryQty) {
                    throw new RuntimeException("Insufficient physical stock for product: " + product.getName()
                            + " | Available in all active batches: " + totalAvailable
                            + " | Requested: " + secondaryQty);
                }
                if (batch.getSecondarySoftReserved() != null) {
                    int newReserved = batch.getSecondarySoftReserved() - secondaryQty;
                    batch.setSecondarySoftReserved(Math.max(0, newReserved));
                    stockBatchRepository.saveAndFlush(batch);
                }
                targetBatchId = null;
            } else {
                if (batch.getSecondarySoftReserved() != null) {
                    int newReserved = batch.getSecondarySoftReserved() - secondaryQty;
                    batch.setSecondarySoftReserved(Math.max(0, newReserved));
                    stockBatchRepository.saveAndFlush(batch);
                }
            }

            // Deduct actual stock
            List<BatchDeductionRecord> depletions;
            if (isPrimary) {
                depletions = stockService.deductByPrimary(product.getId(), qty, targetBatchId, username, bill.getBillNumber(), ratePerSecondary, "Confirmed bill " + bill.getBillNumber());
            } else {
                depletions = stockService.deductBySecondary(product.getId(), qty, targetBatchId, username, bill.getBillNumber(), ratePerSecondary, "Confirmed bill " + bill.getBillNumber());
            }

            item.getBatchDeductions().clear();
            if (!depletions.isEmpty()) {
                StockBatch firstBatch = stockBatchRepository.findById(depletions.get(0).getBatchId())
                        .orElseThrow(() -> new RuntimeException("Batch not found: " + depletions.get(0).getBatchId()));
                item.setBatch(firstBatch);
            }
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

        customer.setLastOrderAt(LocalDateTime.now());
        customerRepository.save(customer);

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

    @Transactional(rollbackFor = RuntimeException.class)
    public List<BillService.BulkConfirmResult> bulkConfirmBills(List<UUID> billIds) {
        return bulkConfirmBills(billIds, "System");
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public List<BillService.BulkConfirmResult> bulkConfirmBills(List<UUID> billIds, String username) {
        List<BillService.BulkConfirmResult> results = new ArrayList<>();
        for (UUID id : billIds) {
            try {
                confirmBill(id, username);
                results.add(new BillService.BulkConfirmResult(id, true, "Confirmed successfully"));
            } catch (Exception e) {
                results.add(new BillService.BulkConfirmResult(id, false, e.getMessage()));
            }
        }
        return results;
    }
}

