package com.shop.modules.billing;

import com.shop.modules.billing.dto.BillItemResponse;
import com.shop.modules.billing.dto.BillResponse;
import com.shop.modules.billing.dto.CreateBillRequest;
import com.shop.modules.billing.dto.ReturnItemsRequest;
import com.shop.modules.customer.CustomerService;
import com.shop.modules.product.ProductService;
import com.shop.modules.customer.Customer;
import com.shop.modules.stock.StockBatch;
import com.shop.modules.customer.CustomerRepository;
import com.shop.modules.product.Product;
import com.shop.modules.product.ProductRepository;
import com.shop.modules.stock.Stock;
import com.shop.modules.stock.StockRepository;
import com.shop.modules.stock.StockService;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import com.shop.modules.khata.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillService {

    private final BillRepository billRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StockService stockService;
    private final StockRepository stockRepository;
    private final CustomerService customerService;
    private final ProductService productService;
    private final com.shop.modules.stock.StockBatchRepository stockBatchRepository;
    private final PaymentRepository paymentRepository;

    // ── Convert entity to response DTO ──
    private BillResponse toResponse(Bill bill) {

        BigDecimal gst5 = BigDecimal.ZERO;
        BigDecimal gst12 = BigDecimal.ZERO;
        BigDecimal gst18 = BigDecimal.ZERO;
        BigDecimal gst28 = BigDecimal.ZERO;
        int totalQuantity = 0;
        List<BillItemResponse> itemResponses =
                new ArrayList<>();

        for (BillItem item : bill.getItems()) {
            totalQuantity += item.getQuantity();

            BigDecimal itemSubtotal = item.getRate()
                    .multiply(BigDecimal.valueOf(
                            item.getQuantity()));

            int gstRate =
                    item.getGstPercent().intValue();
            switch (gstRate) {
                case 5  -> gst5  =
                        gst5.add(item.getGstAmount());
                case 12 -> gst12 =
                        gst12.add(item.getGstAmount());
                case 18 -> gst18 =
                        gst18.add(item.getGstAmount());
                case 28 -> gst28 =
                        gst28.add(item.getGstAmount());
            }

            itemResponses.add(BillItemResponse.builder()
                    .productName(
                            item.getProduct().getName())
                    .brand(item.getProduct().getBrand())
                    .unitType(item.getUnitType())
                    .quantity(item.getQuantity())
                    .freeQuantity(item.getFreeQuantity())
                    .rate(item.getRate())
                    .gstPercent(item.getGstPercent())
                    .gstAmount(item.getGstAmount())
                    .cessPercent(item.getCessPercent())
                    .cessAmount(item.getCessAmount())
                    .total(item.getTotal())
                    .build());
        }

        // Build GST summary string
        StringBuilder gstSummary =
                new StringBuilder();
        if (gst5.compareTo(BigDecimal.ZERO) > 0)
            gstSummary.append("5%: ₹")
                    .append(gst5).append(" ");
        if (gst12.compareTo(BigDecimal.ZERO) > 0)
            gstSummary.append("12%: ₹")
                    .append(gst12).append(" ");
        if (gst18.compareTo(BigDecimal.ZERO) > 0)
            gstSummary.append("18%: ₹")
                    .append(gst18).append(" ");
        if (gst28.compareTo(BigDecimal.ZERO) > 0)
            gstSummary.append("28%: ₹")
                    .append(gst28).append(" ");

        String areaName =
                bill.getCustomer().getArea() != null
                        ? bill.getCustomer()
                        .getArea().getName() : null;

        return BillResponse.builder()
                .id(bill.getId())
                .billNumber(bill.getBillNumber())
                .status(bill.getStatus())
                .createdAt(bill.getCreatedAt())
                .createdBy(bill.getCreatedBy() != null
                        ? bill.getCreatedBy().getName()
                        : null)
                .customerId(bill.getCustomer().getId())
                .customerName(
                        bill.getCustomer().getName())
                .customerShopName(
                        bill.getCustomer().getShopName())
                .customerPhone(
                        bill.getCustomer().getPhone())
                .customerArea(areaName)
                .subtotal(bill.getSubtotal())
                .gstTotal(bill.getGstTotal())
                .cessTotal(bill.getCessTotal())
                .gstSummary(gstSummary.toString().trim())
                .discount(bill.getDiscount())
                .grandTotal(bill.getGrandTotal())
                .paymentMode(bill.getPaymentMode())
                .paidAmount(bill.getPaidAmount())
                .pendingAmount(bill.getPendingAmount())
                .fullyPaid(bill.getPendingAmount()
                        .compareTo(BigDecimal.ZERO) == 0)
                .items(itemResponses)
                .totalItems(itemResponses.size())
                .totalQuantity(totalQuantity)
                .build();
    }

    private void recalculateCustomerPending(Customer customer) {
        BigDecimal totalGeneralPayments = paymentRepository.findByCustomerIdOrderByPaidAtDesc(customer.getId())
                .stream()
                .filter(p -> p.getBill() == null)
                .map(p -> p.getAppliedAmount() != null ? p.getAppliedAmount() : p.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal unpaidOpeningBalance = customer.getOpeningBalance() != null
                ? customer.getOpeningBalance().subtract(totalGeneralPayments)
                : BigDecimal.ZERO;
        if (unpaidOpeningBalance.compareTo(BigDecimal.ZERO) < 0) {
            unpaidOpeningBalance = BigDecimal.ZERO;
        }

        BigDecimal totalBillPending = billRepository.findByCustomerId(customer.getId())
                .stream()
                .filter(b -> b.getStatus() != BillStatus.CANCELLED)
                .map(Bill::getPendingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        customer.setTotalPending(unpaidOpeningBalance.add(totalBillPending));
        customerRepository.save(customer);
    }

    // ── Get all bills ──
    public List<BillResponse> getAllBills() {
        return billRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Get bill by id ──
    public BillResponse getBillById(UUID id) {
        return toResponse(
                billRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Bill not found: " + id)));
    }

    // ── Get pending bills ──
    public List<BillResponse> getPendingBills() {
        return billRepository.findPendingBills()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Get customer history ──
    public List<BillResponse> getCustomerHistory(
            UUID customerId) {
        return billRepository
                .findByCustomerIdOrderByCreatedAtDesc(
                        customerId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Create bill ──
    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse createBill(
            CreateBillRequest req,
            String createdByPhone) {

        // Validate customer
        Customer customer = customerService.findCustomerByIdentifier(req.getCustomerId());

        // Validate user
        User user = userRepository
                .findByPhone(createdByPhone)
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"));

        // Validate items not empty
        if (req.getItems() == null
                || req.getItems().isEmpty()) {
            throw new RuntimeException(
                    "Bill must have at least one item");
        }

        // Validate PARTIAL payment
        if (req.getPaymentMode()
                == PaymentMode.PARTIAL
                && (req.getPaidAmount() == null
                || req.getPaidAmount()
                .compareTo(BigDecimal.ZERO)
                <= 0)) {
            throw new RuntimeException(
                    "Paid amount required for "
                            + "PARTIAL payment");
        }

        // Validate NPA customer credit block
        if (customer.getIsNpa() != null && customer.getIsNpa()) {
            if (req.getPaymentMode() == PaymentMode.UDHAR || req.getPaymentMode() == PaymentMode.PARTIAL) {
                throw new RuntimeException("Credit sales are blocked for NPA customer: " 
                        + customer.getName() + " — CASH mode only");
            }
        }

        // Check if draft
        BillStatus targetStatus = req.getStatus() != null ? req.getStatus() : BillStatus.CONFIRMED;
        boolean isDraft = (targetStatus == BillStatus.DRAFT);

        // Check all stock first before creating bill
        for (CreateBillRequest.BillItemRequest itemReq
                : req.getItems()) {
            Product product = productService.findProductByIdentifier(itemReq.getProductId());
            checkStockAvailability(product, itemReq, isDraft);
        }

        String billNumber = generateBillNumber();

        Bill bill = Bill.builder()
                .billNumber(billNumber)
                .customer(customer)
                .paymentMode(req.getPaymentMode())
                .discount(req.getDiscount() != null
                        ? req.getDiscount()
                        : BigDecimal.ZERO)
                .notes(req.getNotes())
                .createdBy(user)
                .status(targetStatus)
                .items(new ArrayList<>())
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal gstTotal = BigDecimal.ZERO;
        BigDecimal cessTotal = BigDecimal.ZERO;

        for (CreateBillRequest.BillItemRequest itemReq
                : req.getItems()) {

            Product product = productService.findProductByIdentifier(itemReq.getProductId());

            // Get rate based on unit type
            BigDecimal rate = getRateForUnit(
                    product,
                    itemReq.getUnitType().name());

            // Calculate GST
            BigDecimal itemSubtotal = rate
                    .multiply(BigDecimal.valueOf(
                            itemReq.getQuantity()));

            BigDecimal itemGstPercent = itemReq.getGstPercent() != null ? itemReq.getGstPercent() : product.getGstPercent();
            BigDecimal gstRate = itemGstPercent
                    .divide(BigDecimal.valueOf(100));

            BigDecimal gstAmount = itemSubtotal
                    .multiply(gstRate)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal itemCessPercent = itemReq.getCessPercent() != null ? itemReq.getCessPercent() : (product.getCessPercent() != null ? product.getCessPercent() : BigDecimal.ZERO);
            BigDecimal cessRate = itemCessPercent
                    .divide(BigDecimal.valueOf(100));

            BigDecimal cessAmount = itemSubtotal
                    .multiply(cessRate)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal itemTotal =
                    itemSubtotal.add(gstAmount).add(cessAmount);

            // Get source batch
            StockBatch linkedBatch = null;
            if (itemReq.getBatchId() != null) {
                linkedBatch = stockService.getBatchById(itemReq.getBatchId());
            } else {
                List<StockBatch> activeBatches = stockService.getBatchesByProduct(product.getId());
                linkedBatch = activeBatches.stream()
                        .filter(b -> b.getSecondaryRemaining() > 0)
                        .findFirst()
                        .orElse(!activeBatches.isEmpty() ? activeBatches.get(0) : null);
            }

            BillItem item = BillItem.builder()
                    .bill(bill)
                    .product(product)
                    .batch(linkedBatch)
                    .unitType(itemReq.getUnitType())
                    .quantity(itemReq.getQuantity())
                    .freeQuantity(
                            itemReq.getFreeQuantity())
                    .rate(rate)
                    .gstPercent(itemGstPercent)
                    .gstAmount(gstAmount)
                    .cessPercent(itemCessPercent)
                    .cessAmount(cessAmount)
                    .total(itemTotal)
                    .build();

            bill.getItems().add(item);
            subtotal = subtotal.add(itemSubtotal);
            gstTotal = gstTotal.add(gstAmount);
            cessTotal = cessTotal.add(cessAmount);

            // Deduct stock
            String unitType =
                    itemReq.getUnitType().name();
            boolean isPrimary = unitType
                    .equalsIgnoreCase(
                            product.getPrimaryUnit());

            int totalQtyToDeduct = itemReq.getQuantity() + itemReq.getFreeQuantity();

            if (isDraft) {
                if (linkedBatch != null) {
                    int totalSecondaryRequested = isPrimary
                            ? totalQtyToDeduct * product.getSecondaryPerPrimary()
                            : totalQtyToDeduct;
                    linkedBatch.setSecondarySoftReserved(
                            (linkedBatch.getSecondarySoftReserved() != null ? linkedBatch.getSecondarySoftReserved() : 0) + totalSecondaryRequested);
                    stockBatchRepository.save(linkedBatch);
                }
            } else {
                if (isPrimary) {
                    stockService.deductByPrimary(
                            product.getId(),
                            totalQtyToDeduct,
                            itemReq.getBatchId());
                } else {
                    stockService.deductBySecondary(
                            product.getId(),
                            totalQtyToDeduct,
                            itemReq.getBatchId());
                }
            }
        }

        // Calculate grand total
        BigDecimal grandTotal = subtotal
                .add(gstTotal)
                .add(cessTotal)
                .subtract(bill.getDiscount());

        // Validate discount
        if (bill.getDiscount()
                .compareTo(grandTotal) > 0) {
            throw new RuntimeException(
                    "Discount cannot exceed "
                            + "grand total");
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
            case PARTIAL -> {
                BigDecimal paid =
                        req.getPaidAmount();
                if (paid.compareTo(grandTotal) > 0) {
                    throw new RuntimeException(
                            "Paid amount cannot exceed "
                                    + "grand total of "
                                    + grandTotal);
                }
                bill.setPaidAmount(paid);
                bill.setPendingAmount(
                        grandTotal.subtract(paid));
            }
            default -> {
                bill.setPaidAmount(grandTotal);
                bill.setPendingAmount(
                        BigDecimal.ZERO);
            }
        }

        if (targetStatus != BillStatus.DRAFT && targetStatus != BillStatus.CANCELLED) {
            if (bill.getPendingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                bill.setStatus(BillStatus.PAID);
            } else if (bill.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
                bill.setStatus(BillStatus.PARTIAL);
            } else {
                bill.setStatus(BillStatus.CONFIRMED);
            }
        }

        // Validate credit limit
        if (!isDraft && (req.getPaymentMode() == PaymentMode.UDHAR || req.getPaymentMode() == PaymentMode.PARTIAL)) {
            BigDecimal projectedPending = customer.getTotalPending().add(bill.getPendingAmount());
            BigDecimal limit = customerService.calculateEffectiveCreditLimit(customer);
            if (projectedPending.compareTo(limit) > 0) {
                throw new RuntimeException("Credit limit exceeded for customer: " + customer.getName()
                    + " | Credit Limit: ₹" + limit
                    + " | Current Pending: ₹" + customer.getTotalPending()
                    + " | Requested Credit: ₹" + bill.getPendingAmount()
                    + " | Projected Pending: ₹" + projectedPending);
            }
        }

        if (!isDraft) {
            customer.setLastOrderAt(
                    LocalDateTime.now());
            customerRepository.save(customer);
        }

        Bill savedBill = billRepository.save(bill);
        if (!isDraft) {
            recalculateCustomerPending(customer);
        }
        return toResponse(savedBill);
    }

    // ── Check stock availability ──
    private void checkStockAvailability(
            Product product,
            CreateBillRequest.BillItemRequest itemReq,
            boolean isDraft) {

        Stock stock = stockService.getOrCreateStock(product.getId());

        String unitType =
                itemReq.getUnitType().name();

        boolean isPrimary = unitType
                .equalsIgnoreCase(
                        product.getPrimaryUnit());
        boolean isSecondary = unitType
                .equalsIgnoreCase(
                        product.getSecondaryUnit());

        if (!isPrimary && !isSecondary) {
            throw new RuntimeException(
                    "Invalid unit '"
                            + unitType
                            + "' for: "
                            + product.getName()
                            + " | Valid: "
                            + product.getPrimaryUnit()
                            + " or "
                            + product.getSecondaryUnit());
        }

        int totalQtyRequested = itemReq.getQuantity() + itemReq.getFreeQuantity();
        int totalSecondaryRequested = isPrimary
                ? totalQtyRequested * product.getSecondaryPerPrimary()
                : totalQtyRequested;

        if (itemReq.getBatchId() != null) {
            StockBatch batch = stockService.getBatchById(itemReq.getBatchId());
            int reserved = batch.getSecondarySoftReserved() != null ? batch.getSecondarySoftReserved() : 0;
            int available = isDraft ? (batch.getSecondaryRemaining() - reserved) : batch.getSecondaryRemaining();
            if (available < totalSecondaryRequested) {
                throw new RuntimeException("Insufficient " + (isDraft ? "virtual " : "") + "stock in batch " + batch.getBatchNumber()
                        + " for: " + product.getName()
                        + " | Available: " + available
                        + " | Requested: " + totalSecondaryRequested);
            }
        } else {
            List<StockBatch> activeBatches = stockService.getBatchesByProduct(product.getId());
            if (activeBatches.isEmpty()) {
                throw new RuntimeException("No active stock batch found for: " + product.getName());
            }
            int totalAvailable = 0;
            for (StockBatch b : activeBatches) {
                int reserved = b.getSecondarySoftReserved() != null ? b.getSecondarySoftReserved() : 0;
                int avail = isDraft ? (b.getSecondaryRemaining() - reserved) : b.getSecondaryRemaining();
                if (avail > 0) {
                    totalAvailable += avail;
                }
            }
            if (totalAvailable < totalSecondaryRequested) {
                throw new RuntimeException("Insufficient " + (isDraft ? "virtual " : "") + "stock"
                        + " for: " + product.getName()
                        + " | Available: " + totalAvailable
                        + " | Requested: " + totalSecondaryRequested);
            }
        }
    }

    // ── Get rate for unit type ──
    private BigDecimal getRateForUnit(
            Product product,
            String unitType) {

        boolean isPrimary = unitType
                .equalsIgnoreCase(
                        product.getPrimaryUnit());

        if (isPrimary) {
            if (product.getSellPricePrimary() == null
                    || product.getSellPricePrimary()
                    .compareTo(
                            BigDecimal.ZERO) == 0) {
                throw new RuntimeException(
                        "Sell price not set for "
                                + product.getPrimaryUnit()
                                + " of: "
                                + product.getName());
            }
            return product.getSellPricePrimary();
        } else {
            if (product.getSellPriceSecondary()
                    == null
                    || product.getSellPriceSecondary()
                    .compareTo(
                            BigDecimal.ZERO) == 0) {
                throw new RuntimeException(
                        "Sell price not set for "
                                + product.getSecondaryUnit()
                                + " of: "
                                + product.getName());
            }
            return product.getSellPriceSecondary();
        }
    }

    // ── Generate bill number ──
    private String generateBillNumber() {
        Integer max =
                billRepository.findMaxBillSequence();
        int next = (max != null ? max : 0) + 1;
        return String.format("BILL-%05d", next);
    }

    // ── Cancel bill ──
    @Transactional
    public void cancelBill(UUID id) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Bill not found: " + id));

        if (bill.getStatus()
                == BillStatus.CANCELLED) {
            throw new RuntimeException(
                    "Bill is already cancelled");
        }

        if (bill.getStatus() == BillStatus.DRAFT) {
            // Release soft reservations from batches
            for (BillItem item : bill.getItems()) {
                Product product = item.getProduct();
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
            bill.setUpdatedAt(LocalDateTime.now());
            billRepository.save(bill);
            return;
        }

        // Add stock back for each item
        for (BillItem item : bill.getItems()) {

            String unitType =
                    item.getUnitType().name();
            boolean isPrimary = unitType
                    .equalsIgnoreCase(
                            item.getProduct()
                                    .getPrimaryUnit());

            int primaryQty = 0;
            int secondaryQty = 0;

            int totalItemQty = item.getQuantity() + item.getFreeQuantity();

            if (isPrimary) {
                primaryQty = totalItemQty;
                secondaryQty = totalItemQty
                        * item.getProduct()
                        .getSecondaryPerPrimary();
            } else {
                secondaryQty = totalItemQty;
            }

            stockService.addBackStock(
                    item.getProduct().getId(),
                    primaryQty,
                    secondaryQty);

            stockService.restoreStockToBatches(
                    item.getProduct().getId(),
                    secondaryQty);
        }

        bill.setStatus(BillStatus.CANCELLED);

        billRepository.save(bill);
        recalculateCustomerPending(bill.getCustomer());
    }

    // ── Return Items ──
    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse returnItems(UUID billId, ReturnItemsRequest req) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found: " + billId));

        if (bill.getStatus() == BillStatus.CANCELLED) {
            throw new RuntimeException("Cannot return items of a cancelled bill");
        }

        BigDecimal totalRefundAmount = BigDecimal.ZERO;
        BigDecimal totalSubtotalReduction = BigDecimal.ZERO;
        BigDecimal totalGstReduction = BigDecimal.ZERO;
        BigDecimal totalCessReduction = BigDecimal.ZERO;

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

            // Proportional Subtotal reduction (total - gst - cess)
            BigDecimal subtotalReduction = refundAmount.subtract(gstReduction).subtract(cessReduction);

            totalRefundAmount = totalRefundAmount.add(refundAmount);
            totalSubtotalReduction = totalSubtotalReduction.add(subtotalReduction);
            totalGstReduction = totalGstReduction.add(gstReduction);
            totalCessReduction = totalCessReduction.add(cessReduction);

            // Stock return calculations
            String unitType = item.getUnitType().name();
            boolean isPrimary = unitType.equalsIgnoreCase(item.getProduct().getPrimaryUnit());
            int primaryQty = 0;
            int secondaryQty = 0;

            if (isPrimary) {
                primaryQty = reqItem.getQuantityToReturn();
                secondaryQty = reqItem.getQuantityToReturn() * item.getProduct().getSecondaryPerPrimary();
            } else {
                secondaryQty = reqItem.getQuantityToReturn();
            }

            // Add stock back to inventory
            stockService.addBackStock(item.getProduct().getId(), primaryQty, secondaryQty);

            // Restore stock to specific batch
            if (item.getBatch() != null) {
                stockService.addBackStockToSpecificBatch(item.getBatch().getId(), secondaryQty);
            } else {
                stockService.restoreStockToBatches(item.getProduct().getId(), secondaryQty);
            }

            // Update item record
            item.setQuantity(item.getQuantity() - reqItem.getQuantityToReturn());
            item.setTotal(item.getTotal().subtract(refundAmount));
            item.setGstAmount(item.getGstAmount().subtract(gstReduction));
            item.setCessAmount(item.getCessAmount().subtract(cessReduction));
        }

        // Clean up empty bill items
        bill.getItems().removeIf(item -> item.getQuantity() <= 0);

        // Adjust bill totals
        bill.setSubtotal(bill.getSubtotal().subtract(totalSubtotalReduction));
        bill.setGstTotal(bill.getGstTotal().subtract(totalGstReduction));
        bill.setCessTotal(bill.getCessTotal().subtract(totalCessReduction));

        // Adjust grand total, paid, and pending
        BigDecimal oldPending = bill.getPendingAmount();
        BigDecimal newPending = oldPending.subtract(totalRefundAmount);
        BigDecimal pendingReduction = BigDecimal.ZERO;

        if (newPending.compareTo(BigDecimal.ZERO) < 0) {
            pendingReduction = oldPending;
            bill.setPendingAmount(BigDecimal.ZERO);
            BigDecimal paidRefund = totalRefundAmount.subtract(oldPending);
            bill.setPaidAmount(bill.getPaidAmount().subtract(paidRefund));
        } else {
            pendingReduction = totalRefundAmount;
            bill.setPendingAmount(newPending);
        }

        bill.setGrandTotal(bill.getGrandTotal().subtract(totalRefundAmount));

        // Update bill status
        boolean allReturned = bill.getItems().isEmpty();
        if (allReturned) {
            bill.setStatus(BillStatus.CANCELLED);
        }

        Bill savedBill = billRepository.save(bill);
        recalculateCustomerPending(savedBill.getCustomer());
        return toResponse(savedBill);
    }

    // ── Delete bill (ADMIN only) ──
    // Only cancelled bills can be hard-deleted.
    // Stock was already restored when the bill was cancelled,
    // so this is safe — it's a record cleanup only.
    @Transactional
    public void deleteBill(UUID id) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Bill not found: " + id));

        if (bill.getStatus() != BillStatus.CANCELLED) {
            throw new RuntimeException(
                    "Only CANCELLED bills can be deleted. "
                            + "Cancel the bill first.");
        }

        billRepository.delete(bill);
    }

    // ── Update bill details (ADMIN/MANAGER only) ──
    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse updateBillDetails(UUID id, PaymentMode paymentMode, String notes, BillStatus status, BigDecimal paidAmount) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bill not found: " + id));

        Customer customer = bill.getCustomer();

        // 1. Handle Status Change
        if (status != null && status != bill.getStatus()) {
            if (status == BillStatus.CANCELLED) {
                cancelBill(id);
                bill = billRepository.findById(id).orElseThrow();
            } else if (status == BillStatus.CONFIRMED && bill.getStatus() == BillStatus.CANCELLED) {
                restoreBill(id);
                bill = billRepository.findById(id).orElseThrow();
            } else if (status == BillStatus.CONFIRMED && bill.getStatus() == BillStatus.DRAFT) {
                confirmBill(id);
                bill = billRepository.findById(id).orElseThrow();
            }
        }

        // 2. Handle Payment Mode & Paid Amount changes (only if bill is not CANCELLED)
        if (bill.getStatus() != BillStatus.CANCELLED) {
            BigDecimal oldPending = bill.getPendingAmount();
            BigDecimal newPending = oldPending;

            if (paymentMode != null) {
                bill.setPaymentMode(paymentMode);
            }

            if (bill.getPaymentMode() == PaymentMode.UDHAR) {
                bill.setPaidAmount(BigDecimal.ZERO);
                bill.setPendingAmount(bill.getGrandTotal());
                newPending = bill.getGrandTotal();
            } else if (bill.getPaymentMode() == PaymentMode.PARTIAL) {
                BigDecimal paid = paidAmount != null ? paidAmount : bill.getPaidAmount();
                if (paid.compareTo(bill.getGrandTotal()) > 0) {
                    throw new RuntimeException("Paid amount cannot exceed grand total of " + bill.getGrandTotal());
                }
                bill.setPaidAmount(paid);
                bill.setPendingAmount(bill.getGrandTotal().subtract(paid));
                newPending = bill.getGrandTotal().subtract(paid);
            } else {
                // CASH or UPI
                bill.setPaidAmount(bill.getGrandTotal());
                bill.setPendingAmount(BigDecimal.ZERO);
                newPending = BigDecimal.ZERO;
            }

            // Update customer pending balance based on change in pending amount
            if (newPending.compareTo(oldPending) != 0) {
                BigDecimal diff = newPending.subtract(oldPending);

                // If credit increases, check limits & NPA
                if (diff.compareTo(BigDecimal.ZERO) > 0) {
                    if (customer.getIsNpa() != null && customer.getIsNpa()) {
                        throw new RuntimeException("Credit sales are blocked for NPA customer: " 
                                + customer.getName() + " — CASH mode only");
                    }

                    BigDecimal projectedPending = customer.getTotalPending().add(diff);
                    BigDecimal limit = customerService.calculateEffectiveCreditLimit(customer);
                    if (projectedPending.compareTo(limit) > 0) {
                        throw new RuntimeException("Credit limit exceeded for customer: " + customer.getName()
                            + " | Credit Limit: ₹" + limit
                            + " | Current Pending: ₹" + customer.getTotalPending()
                            + " | Additional Credit: ₹" + diff
                            + " | Projected Pending: ₹" + projectedPending);
                    }
                }
            }

            // Derive and update status
            if (bill.getStatus() != BillStatus.CANCELLED && bill.getStatus() != BillStatus.DRAFT) {
                if (bill.getPendingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    bill.setStatus(BillStatus.PAID);
                } else if (bill.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
                    bill.setStatus(BillStatus.PARTIAL);
                } else {
                    bill.setStatus(BillStatus.CONFIRMED);
                }
            }
        }

        // 3. Handle Notes
        if (notes != null) {
            bill.setNotes(notes);
        }

        Bill savedBill = billRepository.save(bill);
        recalculateCustomerPending(customer);
        return toResponse(savedBill);
    }


    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, rollbackFor = RuntimeException.class)
    public BillResponse confirmBill(UUID billId) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found: " + billId));

        if (bill.getStatus() != BillStatus.DRAFT) {
            throw new RuntimeException("Only DRAFT bills can be confirmed. Current status: " + bill.getStatus());
        }

        Customer customer = bill.getCustomer();

        // Validate NPA customer credit block
        if (customer.getIsNpa() != null && customer.getIsNpa()) {
            if (bill.getPaymentMode() == PaymentMode.UDHAR || bill.getPaymentMode() == PaymentMode.PARTIAL) {
                throw new RuntimeException("Credit sales are blocked for NPA customer: " 
                        + customer.getName() + " — CASH mode only");
            }
        }

        // Validate credit limit
        if (bill.getPaymentMode() == PaymentMode.UDHAR || bill.getPaymentMode() == PaymentMode.PARTIAL) {
            BigDecimal projectedPending = customer.getTotalPending().add(bill.getPendingAmount());
            BigDecimal limit = customerService.calculateEffectiveCreditLimit(customer);
            if (projectedPending.compareTo(limit) > 0) {
                throw new RuntimeException("Credit limit exceeded for customer: " + customer.getName()
                    + " | Credit Limit: ₹" + limit
                    + " | Current Pending: ₹" + customer.getTotalPending()
                    + " | Requested Credit: ₹" + bill.getPendingAmount()
                    + " | Projected Pending: ₹" + projectedPending);
            }
        }

        // Validate and deduct stock, and release soft reservations
        for (BillItem item : bill.getItems()) {
            Product product = item.getProduct();
            StockBatch batch = item.getBatch();

            int qty = item.getQuantity() + item.getFreeQuantity();
            boolean isPrimary = item.getUnitType().name().equalsIgnoreCase(product.getPrimaryUnit());
            int secondaryQty = isPrimary ? qty * product.getSecondaryPerPrimary() : qty;

            if (batch == null) {
                throw new RuntimeException("Sourced stock batch missing for product: " + product.getName());
            }

            if (batch.getSecondaryRemaining() < secondaryQty) {
                throw new RuntimeException("Insufficient physical stock in batch " + batch.getBatchNumber()
                        + " for product: " + product.getName()
                        + " | Available: " + batch.getSecondaryRemaining()
                        + " | Requested: " + secondaryQty);
            }

            // Release soft reservation
            if (batch.getSecondarySoftReserved() != null) {
                int newReserved = batch.getSecondarySoftReserved() - secondaryQty;
                batch.setSecondarySoftReserved(Math.max(0, newReserved));
                stockBatchRepository.saveAndFlush(batch);
            }

            // Deduct actual stock
            if (isPrimary) {
                stockService.deductByPrimary(product.getId(), qty, batch.getId());
            } else {
                stockService.deductBySecondary(product.getId(), qty, batch.getId());
            }
        }

        // Update customer pending + last order
        customer.setLastOrderAt(LocalDateTime.now());
        customerRepository.save(customer);

        // Update bill status dynamically
        if (bill.getPendingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            bill.setStatus(BillStatus.PAID);
        } else if (bill.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            bill.setStatus(BillStatus.PARTIAL);
        } else {
            bill.setStatus(BillStatus.CONFIRMED);
        }
        bill.setUpdatedAt(LocalDateTime.now());
        Bill savedBill = billRepository.save(bill);

        recalculateCustomerPending(customer);
        return toResponse(savedBill);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse restoreBill(UUID id) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bill not found: " + id));

        if (bill.getStatus() != BillStatus.CANCELLED) {
            throw new RuntimeException("Only CANCELLED bills can be restored. Current status: " + bill.getStatus());
        }

        Customer customer = bill.getCustomer();

        // Validate NPA customer credit block
        if (customer.getIsNpa() != null && customer.getIsNpa()) {
            if (bill.getPaymentMode() == PaymentMode.UDHAR || bill.getPaymentMode() == PaymentMode.PARTIAL) {
                throw new RuntimeException("Credit sales are blocked for NPA customer: " 
                        + customer.getName() + " — CASH mode only");
            }
        }

        // Validate credit limit
        if (bill.getPaymentMode() == PaymentMode.UDHAR || bill.getPaymentMode() == PaymentMode.PARTIAL) {
            BigDecimal projectedPending = customer.getTotalPending().add(bill.getPendingAmount());
            BigDecimal limit = customerService.calculateEffectiveCreditLimit(customer);
            if (projectedPending.compareTo(limit) > 0) {
                throw new RuntimeException("Credit limit exceeded for customer: " + customer.getName()
                    + " | Credit Limit: ₹" + limit
                    + " | Current Pending: ₹" + customer.getTotalPending()
                    + " | Restoring Bill Pending: ₹" + bill.getPendingAmount()
                    + " | Projected Pending: ₹" + projectedPending);
            }
        }

        // Validate and deduct stock from inventory and batches
        for (BillItem item : bill.getItems()) {
            Product product = item.getProduct();
            StockBatch batch = item.getBatch();

            int qty = item.getQuantity() + item.getFreeQuantity();
            boolean isPrimary = item.getUnitType().name().equalsIgnoreCase(product.getPrimaryUnit());
            int secondaryQty = isPrimary ? qty * product.getSecondaryPerPrimary() : qty;

            if (batch == null) {
                throw new RuntimeException("Sourced stock batch missing for product: " + product.getName());
            }

            if (batch.getSecondaryRemaining() < secondaryQty) {
                throw new RuntimeException("Insufficient physical stock in batch " + batch.getBatchNumber()
                        + " for product: " + product.getName()
                        + " | Available: " + batch.getSecondaryRemaining()
                        + " | Required: " + secondaryQty);
            }

            // Deduct stock
            if (isPrimary) {
                stockService.deductByPrimary(product.getId(), qty, batch.getId());
            } else {
                stockService.deductBySecondary(product.getId(), qty, batch.getId());
            }
        }

        // Update bill status dynamically
        if (bill.getPendingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            bill.setStatus(BillStatus.PAID);
        } else if (bill.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            bill.setStatus(BillStatus.PARTIAL);
        } else {
            bill.setStatus(BillStatus.CONFIRMED);
        }
        bill.setUpdatedAt(LocalDateTime.now());
        
        Bill savedBill = billRepository.save(bill);
        recalculateCustomerPending(customer);
        return toResponse(savedBill);
    }


    @Transactional(rollbackFor = RuntimeException.class)
    public List<BulkConfirmResult> bulkConfirmBills(List<UUID> billIds) {
        List<BulkConfirmResult> results = new ArrayList<>();
        for (UUID id : billIds) {
            try {
                confirmBill(id);
                results.add(new BulkConfirmResult(id, true, "Confirmed successfully"));
            } catch (Exception e) {
                results.add(new BulkConfirmResult(id, false, e.getMessage()));
            }
        }
        return results;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class BulkConfirmResult {
        private UUID billId;
        private boolean success;
        private String message;
    }
}