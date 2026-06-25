package com.shop.modules.billing;

import com.shop.modules.billing.dto.BillItemResponse;
import com.shop.modules.billing.dto.BillResponse;
import com.shop.modules.billing.dto.CreateBillRequest;
import com.shop.modules.billing.dto.ReturnItemsRequest;
import com.shop.modules.customer.CustomerService;
import com.shop.modules.damage.DamageLog;
import com.shop.modules.damage.DamageLogRepository;
import com.shop.modules.damage.ClaimStatus;
import com.shop.modules.damage.DamageReason;
import com.shop.modules.damage.UnitLevel;
import com.shop.modules.product.UnitType;
import com.shop.modules.stock.StockMovementService;
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
import com.shop.modules.user.UserRole;
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
    private final BillEditHistoryRepository billEditHistoryRepository;
    private final com.shop.modules.damage.DamageLogRepository damageLogRepository;
    private final com.shop.modules.stock.StockMovementService stockMovementService;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.shop.modules.delivery.DeliveryService deliveryService;

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
            if (item.getQuantity() > 0) {
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
            }

            itemResponses.add(BillItemResponse.builder()
                    .id(item.getId())
                    .productId(item.getProduct().getId())
                    .productName(
                            item.getProduct().getName())
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

        // Build Cess summary string using TreeMap for sorted order
        java.util.Map<BigDecimal, BigDecimal> cessGroups = new java.util.TreeMap<>();
        for (BillItem item : bill.getItems()) {
            if (item.getCessPercent() != null && item.getCessPercent().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal percent = item.getCessPercent().stripTrailingZeros();
                BigDecimal amount = item.getCessAmount() != null ? item.getCessAmount() : BigDecimal.ZERO;
                cessGroups.put(percent, cessGroups.getOrDefault(percent, BigDecimal.ZERO).add(amount));
            }
        }
        StringBuilder cessSummaryBuilder = new StringBuilder();
        for (java.util.Map.Entry<BigDecimal, BigDecimal> entry : cessGroups.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                cessSummaryBuilder.append(entry.getKey()).append("%: ₹")
                        .append(entry.getValue().setScale(2, RoundingMode.HALF_UP)).append(" ");
            }
        }
        String cessSummary = cessSummaryBuilder.toString().trim();

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
                .cessSummary(cessSummary)
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
                .version(bill.getVersion())
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

        BigDecimal totalBillPending = billRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())
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

    public List<BillResponse> getRecentBills(int limit) {
        return billRepository.findRecentBills(org.springframework.data.domain.PageRequest.of(0, limit))
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

    public BillResponse getBillById(UUID id, String username) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bill not found: " + id));
        User user = userRepository.findByPhone(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        if (user.getRole() == UserRole.SALESMAN || user.getRole() == UserRole.DELIVERY_BOY) {
            if (bill.getCreatedBy() == null || !bill.getCreatedBy().getId().equals(user.getId())) {
                throw new org.springframework.security.access.AccessDeniedException("You are not authorized to view this bill");
            }
        }
        return toResponse(bill);
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

    public com.shop.common.PagedResponse<BillResponse> getBillsPaged(
            BillStatus status,
            Boolean excludeDrafts,
            String search,
            int page,
            int size,
            String sort,
            String username) {
        User user = userRepository.findByPhone(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
        String[] sortParts = sort.split(",");
        org.springframework.data.domain.Sort sortObj = org.springframework.data.domain.Sort.unsorted();
        if (sortParts.length == 2) {
            org.springframework.data.domain.Sort.Direction dir = sortParts[1].equalsIgnoreCase("desc")
                    ? org.springframework.data.domain.Sort.Direction.DESC
                    : org.springframework.data.domain.Sort.Direction.ASC;
            sortObj = org.springframework.data.domain.Sort.by(dir, sortParts[0]);
        }
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, sortObj);

        String searchVal = (search == null || search.trim().isEmpty()) ? null : search.trim();
        UUID salesmanId = (user.getRole() == UserRole.SALESMAN || user.getRole() == UserRole.DELIVERY_BOY) ? user.getId() : null;

        org.springframework.data.domain.Page<Bill> bills = billRepository.findBillsPaged(
                status,
                excludeDrafts != null && excludeDrafts,
                salesmanId,
                searchVal,
                pageable
        );

        List<BillResponse> content = bills.getContent().stream().map(this::toResponse).collect(Collectors.toList());
        return com.shop.common.PagedResponse.<BillResponse>builder()
                .content(content)
                .currentPage(bills.getNumber())
                .totalPages(bills.getTotalPages())
                .totalElements(bills.getTotalElements())
                .size(bills.getSize())
                .first(bills.isFirst())
                .last(bills.isLast())
                .build();
    }

    public com.shop.common.PagedResponse<BillResponse> getCustomerHistoryPaged(
            UUID customerId,
            int page,
            int size,
            String username) {
        User user = userRepository.findByPhone(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
        );

        UUID salesmanId = (user.getRole() == UserRole.SALESMAN || user.getRole() == UserRole.DELIVERY_BOY) ? user.getId() : null;
        org.springframework.data.domain.Page<Bill> bills = billRepository.findCustomerHistoryPaged(customerId, salesmanId, pageable);

        List<BillResponse> content = bills.getContent().stream().map(this::toResponse).collect(Collectors.toList());
        return com.shop.common.PagedResponse.<BillResponse>builder()
                .content(content)
                .currentPage(bills.getNumber())
                .totalPages(bills.getTotalPages())
                .totalElements(bills.getTotalElements())
                .size(bills.getSize())
                .first(bills.isFirst())
                .last(bills.isLast())
                .build();
    }

    // ── Create bill ──
    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse createBill(
            CreateBillRequest req,
            String createdByPhone) {
        return createBill(req, createdByPhone, false);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse createBill(
            CreateBillRequest req,
            String createdByPhone,
            boolean overrideCost) {

        // Validate customer
        Customer customer = customerService.findCustomerByIdentifier(req.getCustomerId());

        // Validate user
        User user = userRepository
                .findByPhone(createdByPhone)
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"));

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
            checkPriceOverrideLimits(product, itemReq, user, overrideCost);
        }

        String billNumber = generateBillNumber();

        Bill bill = Bill.builder()
                .billNumber(billNumber)
                .customer(customer)
                .paymentMode(req.getPaymentMode())
                .partialPaymentMode(req.getPartialPaymentMode())
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

            // Get rate based on unit type (stored inclusive of tax, or overridden by customRate)
            BigDecimal inclusivePrice;
            if (itemReq.getCustomRate() != null && itemReq.getCustomRate().compareTo(BigDecimal.ZERO) >= 0) {
                inclusivePrice = itemReq.getCustomRate();
            } else {
                inclusivePrice = getRateForUnit(
                        product,
                        itemReq.getUnitType().name());
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
                // 1. Calculate line total inclusive of tax (exact expected sum)
                itemTotal = inclusivePrice
                        .multiply(BigDecimal.valueOf(itemReq.getQuantity()))
                        .setScale(2, RoundingMode.HALF_UP);

                // 2. Back-calculate line subtotal (excluding tax)
                itemSubtotal = itemTotal.divide(taxDivisor, 2, RoundingMode.HALF_UP);

                // 3. Calculate GST and Cess at line level
                BigDecimal gstRate = itemGstPercent.divide(BigDecimal.valueOf(100));
                gstAmount = itemSubtotal
                        .multiply(gstRate)
                        .setScale(2, RoundingMode.HALF_UP);

                BigDecimal cessRate = itemCessPercent.divide(BigDecimal.valueOf(100));
                cessAmount = itemSubtotal
                        .multiply(cessRate)
                        .setScale(2, RoundingMode.HALF_UP);

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
                BigDecimal defaultInclusivePrice = getRateForUnit(product, itemReq.getUnitType().name());
                BigDecimal originalTotal = defaultInclusivePrice
                        .multiply(BigDecimal.valueOf(itemReq.getQuantity()))
                        .setScale(2, RoundingMode.HALF_UP);
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
                    .freeQuantity(
                            itemReq.getFreeQuantity())
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
            String unitType =
                    itemReq.getUnitType().name();
            boolean isPrimary = unitType
                    .equalsIgnoreCase(
                            product.getPrimaryUnit());

            int totalQtyToDeduct = itemReq.getQuantity() + itemReq.getFreeQuantity();
            int secondaryQty = isPrimary ? totalQtyToDeduct * product.getSecondaryPerPrimary() : totalQtyToDeduct;

            BigDecimal ratePerSecondary = BigDecimal.ZERO;
            if (!itemReq.isOffer()) {
                if (isPrimary) {
                    ratePerSecondary = rate.divide(BigDecimal.valueOf(getSafeSecondaryPerPrimary(product)), 4, RoundingMode.HALF_UP);
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
                    List<com.shop.modules.stock.dto.BatchDeductionRecord> depletions;
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
            case COD -> {
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

        if (itemReq.isOffer()) {
            if (itemReq.getBatchId() != null) {
                StockBatch batch = stockService.getBatchById(itemReq.getBatchId());
                int reserved = 0;
                int available = batch.getOfferSecondaryRemaining() != null ? batch.getOfferSecondaryRemaining() : 0;
                if (available < totalSecondaryRequested) {
                    throw new RuntimeException("Insufficient offer stock in batch " + batch.getBatchNumber()
                            + " for: " + product.getName()
                            + " | Available: " + available
                            + " | Requested: " + totalSecondaryRequested);
                }
            } else {
                List<StockBatch> activeBatches = stockService.getBatchesByProduct(product.getId());
                int totalAvailable = 0;
                for (StockBatch b : activeBatches) {
                    int avail = b.getOfferSecondaryRemaining() != null ? b.getOfferSecondaryRemaining() : 0;
                    if (avail > 0) {
                        totalAvailable += avail;
                    }
                }
                if (totalAvailable < totalSecondaryRequested) {
                    throw new RuntimeException("Insufficient offer stock"
                            + " for: " + product.getName()
                            + " | Available: " + totalAvailable
                            + " | Requested: " + totalSecondaryRequested);
                }
            }
            return;
        }

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
        Long next = billRepository.getNextBillSequence();
        return String.format("BILL-%05d", next);
    }

    private void checkPriceOverrideLimits(
            Product product,
            CreateBillRequest.BillItemRequest itemReq,
            User user,
            boolean overrideCost) {
        
        if (itemReq.isOffer()) {
            return;
        }
        if (itemReq.getCustomRate() == null || itemReq.getCustomRate().compareTo(BigDecimal.ZERO) < 0) {
            return;
        }
        
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }
        
        boolean isPrimary = itemReq.getUnitType().name().equalsIgnoreCase(product.getPrimaryUnit());
        BigDecimal costLimit = isPrimary ? product.getBuyPriceWithTax()
            : product.getBuyPriceWithTax().divide(BigDecimal.valueOf(getSafeSecondaryPerPrimary(product)), 4, RoundingMode.HALF_UP);
            
        if (itemReq.getCustomRate().compareTo(costLimit) < 0) {
            if (user.getRole() == UserRole.SALESMAN || user.getRole() == UserRole.DELIVERY_BOY) {
                throw new RuntimeException("Price override not allowed: Custom rate for " 
                        + product.getName() + " cannot be lower than purchase cost of ₹" + costLimit);
            } else if (user.getRole() == UserRole.MANAGER) {
                if (!overrideCost) {
                    throw new com.shop.common.BelowCostWarningException("Warning: Custom rate for " 
                            + product.getName() + " (₹" + itemReq.getCustomRate() + ") is lower than purchase cost of ₹" + costLimit);
                }
            }
        }
    }

    // ── Cancel bill ──
    @Transactional
    public void cancelBill(UUID id) {
        cancelBill(id, "System");
    }

    @Transactional
    public void cancelBill(UUID id, String username) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() ->
                        new jakarta.persistence.EntityNotFoundException(
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
        recalculateCustomerPending(bill.getCustomer());
    }

    // ── Return Items ──
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

            // Proportional Subtotal reduction (total - gst - cess)
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
                secondaryQty = reqItem.getQuantityToReturn() * getSafeSecondaryPerPrimary(item.getProduct());
            } else {
                secondaryQty = reqItem.getQuantityToReturn();
            }

            BigDecimal ratePerSecondary = isPrimary ? item.getRate().divide(BigDecimal.valueOf(getSafeSecondaryPerPrimary(item.getProduct())), 4, RoundingMode.HALF_UP) : item.getRate();

            boolean isDamaged = "DAMAGED".equalsIgnoreCase(reqItem.getReturnCondition());

            if (isDamaged) {
                // Skip stock restoration
                StockBatch batch = item.getBatch();
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
                            batch = deduction.getBatch();
                        }
                    }
                    item.getBatchDeductions().removeIf(d -> d.getQuantityDeducted() <= 0);
                }

                // Create and save DamageLog record
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

                // Log a "DAMAGE" movement (negative quantity)
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
            } else {
                // GOOD condition - perform normal restoration
                if (item.getBatchDeductions() != null && !item.getBatchDeductions().isEmpty()) {
                    int remainingToRestore = secondaryQty;
                    List<BillItemBatchDeduction> deductions = new ArrayList<>(item.getBatchDeductions());
                    deductions.sort((d1, d2) -> d2.getCreatedAt().compareTo(d1.getCreatedAt())); // LIFO return on batch depletions

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
                    // Fallback for legacy bills
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

            // Update item record (keep in DB with quantity=0)
            item.setQuantity(item.getQuantity() - reqItem.getQuantityToReturn());
            item.setTotal(item.getTotal().subtract(refundAmount));
            item.setGstAmount(item.getGstAmount().subtract(gstReduction));
            item.setCessAmount(item.getCessAmount().subtract(cessReduction));

            if (item.getQuantity() <= 0) {
                item.setReturned(true);
            }
        }

        // Adjust bill totals
        bill.setSubtotal(bill.getSubtotal().subtract(totalSubtotalReduction));
        bill.setGstTotal(bill.getGstTotal().subtract(totalGstReduction));
        bill.setCessTotal(bill.getCessTotal().subtract(totalCessReduction));
        bill.setDiscount(bill.getDiscount().subtract(totalDiscountReduction));

        // Adjust grand total, paid, and pending (Gap 1: cash refund vs Udhar credit reduction logic)
        BigDecimal oldPending = bill.getPendingAmount() != null ? bill.getPendingAmount() : BigDecimal.ZERO;
        BigDecimal actualRefund = totalRefundAmount.subtract(oldPending);
        if (actualRefund.compareTo(BigDecimal.ZERO) < 0) {
            actualRefund = BigDecimal.ZERO;
        }

        BigDecimal pendingReduction = totalRefundAmount.min(oldPending);
        bill.setPendingAmount(oldPending.subtract(pendingReduction));
        bill.setGrandTotal(bill.getGrandTotal().subtract(totalRefundAmount));

        // Create negative REFUND payment entry ONLY if actualRefund > 0
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

            com.shop.modules.khata.Payment refundPayment = com.shop.modules.khata.Payment.builder()
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

        // Update bill status (all items returned check)
        boolean allReturned = bill.getItems().stream().allMatch(item -> item.getQuantity() <= 0);
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

        // Nullify bill reference on payments and add audit notes to payments
        List<com.shop.modules.khata.Payment> payments = paymentRepository.findByBillIdIn(List.of(bill.getId()));
        for (com.shop.modules.khata.Payment payment : payments) {
            payment.setBill(null);
            String oldNotes = payment.getNotes() != null ? payment.getNotes() : "";
            payment.setNotes((oldNotes + " | (Linked Bill " + bill.getBillNumber() + " was deleted)").trim());
            paymentRepository.save(payment);
        }

        billRepository.delete(bill);
    }

    // ── Update bill details (ADMIN/MANAGER only) ──
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

        // 1. Concurrency Protection (Optimistic Locking Check)
        if (version != null && !version.equals(bill.getVersion())) {
            throw new RuntimeException("This bill has been modified by another user. Please refresh and try again.");
        }

        // 2. Validate Edit Reason for non-draft bills on material edits
        boolean isMaterialChange = (newItems != null || discount != null);
        if (isMaterialChange && bill.getStatus() != BillStatus.DRAFT) {
            if (editReason == null || editReason.trim().isEmpty()) {
                throw new RuntimeException("Edit reason is required for modifying confirmed bills.");
            }
        }

        Customer customer = bill.getCustomer();
        User user = userRepository.findByPhone(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        // Save old snapshot for audit log
        String oldJson = getBillSnapshotJson(bill);

        // 3. Handle Status Change (before new items processed, keep aligned)
        if (status != null && status != bill.getStatus()) {
            if (status == BillStatus.CANCELLED) {
                cancelBill(id, username);
                bill = billRepository.findById(id).orElseThrow();
            } else if (status == BillStatus.CONFIRMED && bill.getStatus() == BillStatus.CANCELLED) {
                restoreBill(id, username);
                bill = billRepository.findById(id).orElseThrow();
            } else if (status == BillStatus.CONFIRMED && bill.getStatus() == BillStatus.DRAFT) {
                confirmBill(id, username);
                bill = billRepository.findById(id).orElseThrow();
            }
        }

        // 4. Stock Restoration Phase
        if (newItems != null && bill.getStatus() != BillStatus.CANCELLED) {
            for (BillItem item : bill.getItems()) {
                Product product = item.getProduct();
                StockBatch batch = item.getBatch();
                int qty = item.getQuantity() + item.getFreeQuantity();
                boolean isPrimary = item.getUnitType().name().equalsIgnoreCase(product.getPrimaryUnit());
                int secondaryQty = isPrimary ? qty * product.getSecondaryPerPrimary() : qty;

                if (bill.getStatus() == BillStatus.DRAFT) {
                    // Restore soft reservation
                    if (batch != null && batch.getSecondarySoftReserved() != null) {
                        int newReserved = batch.getSecondarySoftReserved() - secondaryQty;
                        batch.setSecondarySoftReserved(Math.max(0, newReserved));
                        stockBatchRepository.save(batch);
                    }
                } else {
                    // Restore physical stock batch-wise
                    BigDecimal ratePerSecondary = isPrimary ? item.getRate().divide(BigDecimal.valueOf(getSafeSecondaryPerPrimary(product)), 4, RoundingMode.HALF_UP) : item.getRate();
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

        // 5. Recalculation & Stock Validation/Deduction
        boolean isDraft = (status != null ? status == BillStatus.DRAFT : bill.getStatus() == BillStatus.DRAFT);
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal gstTotal = BigDecimal.ZERO;
        BigDecimal cessTotal = BigDecimal.ZERO;

        if (newItems != null && bill.getStatus() != BillStatus.CANCELLED) {
            // Check stock availability and price override limits first for new items
            for (CreateBillRequest.BillItemRequest itemReq : newItems) {
                Product product = productService.findProductByIdentifier(itemReq.getProductId());
                checkStockAvailability(product, itemReq, isDraft);
                checkPriceOverrideLimits(product, itemReq, user, overrideCost);
            }

            // Populate and deduct stock
            for (CreateBillRequest.BillItemRequest itemReq : newItems) {
                Product product = productService.findProductByIdentifier(itemReq.getProductId());
                boolean isPrimary = itemReq.getUnitType().name().equalsIgnoreCase(product.getPrimaryUnit());

                BigDecimal inclusivePrice;
                if (itemReq.getCustomRate() != null && itemReq.getCustomRate().compareTo(BigDecimal.ZERO) >= 0) {
                    inclusivePrice = itemReq.getCustomRate();
                } else {
                    inclusivePrice = getRateForUnit(product, itemReq.getUnitType().name());
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
                    
                    BigDecimal defaultInclusivePrice = getRateForUnit(product, itemReq.getUnitType().name());
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
                    ratePerSecondary = isPrimary ? rate.divide(BigDecimal.valueOf(getSafeSecondaryPerPrimary(product)), 4, RoundingMode.HALF_UP) : rate;
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

        // 6. Discount & Grand Total Recalculation
        if (discount != null) {
            bill.setDiscount(discount);
        }
        
        BigDecimal computedGrandTotal = bill.getSubtotal()
                .add(bill.getGstTotal())
                .add(bill.getCessTotal())
                .subtract(bill.getDiscount());

        if (bill.getDiscount().compareTo(computedGrandTotal.add(bill.getDiscount())) > 0) {
            throw new RuntimeException("Discount cannot exceed subtotal with taxes");
        }
        bill.setGrandTotal(computedGrandTotal);

        // 7. Payment Reconciliation and Credit Limit Checks (ordered correctly: before save)
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
                // CASH / UPI
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
        recalculateCustomerPending(customer);

        // 8. Capture Audit Log
        String newJson = getBillSnapshotJson(savedBill);
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

        return toResponse(savedBill);
    }

    private String getBillSnapshotJson(Bill bill) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> snapshot = new java.util.HashMap<>();
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
            
            java.util.List<java.util.Map<String, Object>> itemsList = new java.util.ArrayList<>();
            for (BillItem item : bill.getItems()) {
                java.util.Map<String, Object> itemMap = new java.util.HashMap<>();
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
            return mapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            return "{}";
        }
    }


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

        // Run price override checks first
        for (BillItem item : bill.getItems()) {
            CreateBillRequest.BillItemRequest itemReq = new CreateBillRequest.BillItemRequest();
            itemReq.setProductId(item.getProduct().getId().toString());
            itemReq.setQuantity(item.getQuantity());
            itemReq.setFreeQuantity(item.getFreeQuantity());
            itemReq.setUnitType(item.getUnitType());
            itemReq.setCustomRate(item.getRate());
            itemReq.setOffer(item.getOffer() != null && item.getOffer());
            checkPriceOverrideLimits(item.getProduct(), itemReq, user, overrideCost);
        }

        // Validate and deduct stock, and release soft reservations
        for (BillItem item : bill.getItems()) {
            Product product = item.getProduct();
            StockBatch batch = item.getBatch();

            int qty = item.getQuantity() + item.getFreeQuantity();
            boolean isPrimary = item.getUnitType().name().equalsIgnoreCase(product.getPrimaryUnit());
            int secondaryQty = isPrimary ? qty * getSafeSecondaryPerPrimary(product) : qty;

            if (batch == null) {
                throw new RuntimeException("Sourced stock batch missing for product: " + product.getName());
            }

            BigDecimal ratePerSecondary = isPrimary ? item.getRate().divide(BigDecimal.valueOf(getSafeSecondaryPerPrimary(product)), 4, RoundingMode.HALF_UP) : item.getRate();

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
            List<com.shop.modules.stock.dto.BatchDeductionRecord> depletions;
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

            BigDecimal ratePerSecondary = isPrimary ? item.getRate().divide(BigDecimal.valueOf(getSafeSecondaryPerPrimary(product)), 4, RoundingMode.HALF_UP) : item.getRate();

            // Flexible restoration fallback: try original batch first, if not sufficient, fallback to dynamic FIFO
            UUID batchIdToDeduct = null;
            if (batch != null && batch.getSecondaryRemaining() >= secondaryQty && !batch.getExhausted() && batch.getBatchStatus() == com.shop.modules.stock.BatchStatus.ACTIVE) {
                batchIdToDeduct = batch.getId();
            }

            // Deduct stock
            List<com.shop.modules.stock.dto.BatchDeductionRecord> depletions;
            if (isPrimary) {
                depletions = stockService.deductByPrimary(product.getId(), qty, batchIdToDeduct, username, bill.getBillNumber(), ratePerSecondary, "Restored bill " + bill.getBillNumber());
            } else {
                depletions = stockService.deductBySecondary(product.getId(), qty, batchIdToDeduct, username, bill.getBillNumber(), ratePerSecondary, "Restored bill " + bill.getBillNumber());
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
        return bulkConfirmBills(billIds, "System");
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public List<BulkConfirmResult> bulkConfirmBills(List<UUID> billIds, String username) {
        List<BulkConfirmResult> results = new ArrayList<>();
        for (UUID id : billIds) {
            try {
                confirmBill(id, username);
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

    public List<BillEditHistory> getBillEditHistory(UUID billId) {
        if (!billRepository.existsById(billId)) {
            throw new jakarta.persistence.EntityNotFoundException("Bill not found: " + billId);
        }
        return billEditHistoryRepository.findByBillIdOrderByEditedAtDesc(billId);
    }

    private int getSafeSecondaryPerPrimary(Product product) {
        if (product == null || product.getSecondaryPerPrimary() == null || product.getSecondaryPerPrimary() <= 0) {
            return 1;
        }
        return product.getSecondaryPerPrimary();
    }
}