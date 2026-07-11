package com.shop.modules.stock;

import com.shop.modules.product.Product;
import com.shop.modules.product.ProductRepository;
import com.shop.modules.stock.dto.ReceiveStockRequest;
import com.shop.modules.expense.Expense;
import com.shop.modules.expense.ExpenseCategory;
import com.shop.modules.expense.ExpenseRepository;
import com.shop.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockReceiveService {

    private final StockBatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final StockMovementService movementService;
    private final StockInventoryService inventoryService;

    @Transactional
    public StockBatch receiveStock(
            StockService.ReceiveStockRequest req, String addedByUsername) {

        Product product = productRepository.findById(req.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found: " + req.getProductId()));

        if (product.getSecondaryPerPrimary() == null || product.getSecondaryPerPrimary() <= 0) {
            throw new RuntimeException("Product unit config missing — set secondaryPerPrimary first");
        }

        // ── TOP-UP LOGIC ────────────────────────────────────────────────────────
        // Check if a non-exhausted batch already exists for this product + batchNumber
        if (req.getBatchNumber() != null && !req.getBatchNumber().isBlank()) {
            java.util.Optional<StockBatch> existingOpt =
                batchRepository.findByProductIdAndBatchNumberIgnoreCaseAndExhaustedFalse(
                    product.getId(), req.getBatchNumber().trim());

            if (existingOpt.isPresent()) {
                StockBatch existing = existingOpt.get();

                // Top-Up batch directly even if price is slightly different (e.g. due to scheme discounts)
                return topUpBatch(existing, product, req, addedByUsername);
            }
            // If exhausted batch exists with same number → fall through and create new batch (allowed)
        }
        // ────────────────────────────────────────────────────────────────────────

        // Update product master prices (only for NEW batch, not top-up)

        product.setBuyPriceWithoutTax(req.getBuyPriceWithoutTax());
        product.calculateBuyPriceWithTax();

        if (req.getSellPricePrimary() != null && req.getSellPricePrimary().compareTo(BigDecimal.ZERO) > 0) {
            product.setSellPricePrimary(req.getSellPricePrimary());
        }
        if (req.getSellPriceSecondary() != null && req.getSellPriceSecondary().compareTo(BigDecimal.ZERO) > 0) {
            product.setSellPriceSecondary(req.getSellPriceSecondary());
        }
        productRepository.save(product);

        int secondaryFromPrimary = req.getPrimaryReceived() * product.getSecondaryPerPrimary();
        int totalSecondary = secondaryFromPrimary + req.getExtraSecondaryReceived();

        BigDecimal gstPercent = req.getGstPercent() != null && req.getGstPercent().compareTo(BigDecimal.ZERO) >= 0
                ? req.getGstPercent()
                : product.getGstPercent();

        BigDecimal gstRate = gstPercent.divide(BigDecimal.valueOf(100));
        BigDecimal taxAmount = req.getBuyPriceWithoutTax().multiply(gstRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal buyPriceWithTax = req.getBuyPriceWithoutTax().add(taxAmount);

        int offerUnits = req.getOfferSecondaryReceived();

        // Save batch
        StockBatch batch = StockBatch.builder()
                .product(product)
                .batchNumber(req.getBatchNumber())
                .invoiceNumber(req.getInvoiceNumber() != null ? req.getInvoiceNumber() : req.getSupplierInvoiceNumber())
                .primaryReceived(req.getPrimaryReceived())
                .secondaryReceived(totalSecondary)
                .secondaryRemaining(totalSecondary)
                .offerSecondaryReceived(offerUnits)
                .offerSecondaryRemaining(offerUnits)
                .buyPriceWithoutTax(req.getBuyPriceWithoutTax())
                .buyPriceWithTax(buyPriceWithTax)
                .gstPercent(gstPercent)
                .expiryDate(req.getExpiryDate())
                .supplierName(req.getSupplierName())
                .supplierInvoiceDate(req.getSupplierInvoiceDate())
                .stockReceivedDate(req.getStockReceivedDate() != null ? req.getStockReceivedDate() : LocalDate.now())
                .manufacturingDate(req.getManufacturingDate())
                .remarks(req.getRemarks())
                .batchStatus(BatchStatus.ACTIVE)
                .exhausted(false)
                .receiveSource(req.getReceiveSource() != null ? req.getReceiveSource() : "BULK_RECEIVE")
                .build();

        batchRepository.save(batch);

        Stock stock = inventoryService.getOrCreateStock(product.getId());
        int qtyBefore = stock.getTotalSecondaryUnits();
        int receivedQty = totalSecondary + offerUnits;
        int qtyAfter = qtyBefore + receivedQty;

        stock.setTotalSecondaryUnits(qtyAfter);
        inventoryService.normalizeStock(stock, product);
        stockRepository.save(stock);

        // Log paid units movement
        String movementType = "PURCHASE";
        if (req.getBatchNumber() != null && 
            (req.getBatchNumber().toUpperCase().contains("OPENING") || 
             req.getBatchNumber().toUpperCase().contains("INITIAL"))) {
            movementType = "OPENING_STOCK";
        }

        movementService.logMovement(
                product,
                batch,
                movementType,
                totalSecondary,
                qtyBefore,
                qtyBefore + totalSecondary,
                batch.getBuyPricePerSecondary(product.getSecondaryPerPrimary()),
                addedByUsername,
                batch.getInvoiceNumber(),
                req.getRemarks()
        );

        // Log offer/free units movement (if any)
        if (offerUnits > 0) {
            movementService.logMovement(
                    product,
                    batch,
                    "OFFER_RECEIVE",
                    offerUnits,
                    qtyBefore + totalSecondary,
                    qtyBefore + totalSecondary + offerUnits,
                    BigDecimal.ZERO,
                    addedByUsername,
                    batch.getInvoiceNumber(),
                    "Free offer units received"
            );
        }

        // Auto-log stock purchase as expense
        if (req.isLogAsExpense()) {
            BigDecimal primaryCost = buyPriceWithTax.multiply(BigDecimal.valueOf(req.getPrimaryReceived()));
            BigDecimal secondaryCost = BigDecimal.ZERO;
            if (req.getExtraSecondaryReceived() > 0 && product.getSecondaryPerPrimary() > 0) {
                BigDecimal buyPricePerSecondary = buyPriceWithTax.divide(
                        BigDecimal.valueOf(product.getSecondaryPerPrimary()),
                        4,
                        RoundingMode.HALF_UP
                );
                secondaryCost = buyPricePerSecondary.multiply(BigDecimal.valueOf(req.getExtraSecondaryReceived()));
            }
            BigDecimal totalPurchaseCost = primaryCost.add(secondaryCost).setScale(2, RoundingMode.HALF_UP);

            if (totalPurchaseCost.compareTo(BigDecimal.ZERO) > 0) {
                com.shop.modules.user.User user = null;
                if (addedByUsername != null && !addedByUsername.equals("System")) {
                    user = userRepository.findByPhone(addedByUsername).orElse(null);
                }

                String qtyDesc = "";
                if (req.getPrimaryReceived() > 0 && req.getExtraSecondaryReceived() > 0) {
                    qtyDesc = req.getPrimaryReceived() + " " + (product.getPrimaryUnit() != null ? product.getPrimaryUnit() : "BOX") 
                            + " + " + req.getExtraSecondaryReceived() + " " + (product.getSecondaryUnit() != null ? product.getSecondaryUnit() : "LADI");
                } else if (req.getPrimaryReceived() > 0) {
                    qtyDesc = req.getPrimaryReceived() + " " + (product.getPrimaryUnit() != null ? product.getPrimaryUnit() : "BOX");
                } else {
                    qtyDesc = req.getExtraSecondaryReceived() + " " + (product.getSecondaryUnit() != null ? product.getSecondaryUnit() : "LADI");
                }

                ExpenseCategory category = ExpenseCategory.STOCK_PURCHASE;
                String desc;
                if (movementType.equals("OPENING_STOCK")) {
                    category = ExpenseCategory.OPENING_STOCK;
                    desc = String.format("Opening Stock: %s of %s (Batch: %s)", qtyDesc, product.getName(), req.getBatchNumber());
                } else {
                    desc = String.format("Stock Purchase: %s of %s from %s (Batch: %s)", qtyDesc, product.getName(), req.getSupplierName(), req.getBatchNumber());
                }

                Expense expense = Expense.builder()
                        .category(category)
                        .amount(totalPurchaseCost)
                        .description(desc)
                        .expenseDate(LocalDate.now())
                        .addedBy(user)
                        .build();

                expenseRepository.save(expense);
            }
        }

        return batch;
    }

    /**
     * Top-up an existing non-exhausted batch with additional stock (same price).
     * Updates qty fields, GST, dates. Does NOT change batch price or product sell price.
     */
    @Transactional
    private StockBatch topUpBatch(
            StockBatch existing,
            Product product,
            StockService.ReceiveStockRequest req,
            String addedByUsername) {

        int secondaryFromPrimary = req.getPrimaryReceived() * product.getSecondaryPerPrimary();
        int newSecondary = secondaryFromPrimary + req.getExtraSecondaryReceived();
        int newOffer = req.getOfferSecondaryReceived();
        int newTotal = newSecondary + newOffer;

        // Update batch quantities
        existing.setPrimaryReceived((existing.getPrimaryReceived() != null ? existing.getPrimaryReceived() : 0) + req.getPrimaryReceived());
        existing.setSecondaryReceived((existing.getSecondaryReceived() != null ? existing.getSecondaryReceived() : 0) + newSecondary);
        existing.setSecondaryRemaining((existing.getSecondaryRemaining() != null ? existing.getSecondaryRemaining() : 0) + newSecondary);
        existing.setOfferSecondaryReceived((existing.getOfferSecondaryReceived() != null ? existing.getOfferSecondaryReceived() : 0) + newOffer);
        existing.setOfferSecondaryRemaining((existing.getOfferSecondaryRemaining() != null ? existing.getOfferSecondaryRemaining() : 0) + newOffer);

        // Update GST to latest (govt rate, product-level)
        BigDecimal latestGst = req.getGstPercent() != null && req.getGstPercent().compareTo(java.math.BigDecimal.ZERO) >= 0
                ? req.getGstPercent() : product.getGstPercent();
        existing.setGstPercent(latestGst);

        // Update supplier/date info to latest delivery
        if (req.getSupplierInvoiceDate() != null) existing.setSupplierInvoiceDate(req.getSupplierInvoiceDate());
        if (req.getStockReceivedDate() != null) existing.setStockReceivedDate(req.getStockReceivedDate());
        if (req.getSupplierName() != null && !req.getSupplierName().isBlank()) existing.setSupplierName(req.getSupplierName());

        // Mark active in case it was somehow set inactive
        existing.setExhausted(false);
        existing.setBatchStatus(BatchStatus.ACTIVE);

        batchRepository.save(existing);

        // Update aggregate stock
        Stock stock = inventoryService.getOrCreateStock(product.getId());
        int qtyBefore = stock.getTotalSecondaryUnits();
        int qtyAfter = qtyBefore + newTotal;
        stock.setTotalSecondaryUnits(qtyAfter);
        inventoryService.normalizeStock(stock, product);
        stockRepository.save(stock);

        // Log movement as PURCHASE_TOPUP (separate from original PURCHASE)
        String newInvoice = req.getSupplierInvoiceNumber() != null ? req.getSupplierInvoiceNumber() : req.getInvoiceNumber();
        // Log paid units top-up movement
        BigDecimal scanUnitPricePerSecondary = req.getBuyPriceWithoutTax() != null 
                ? req.getBuyPriceWithoutTax().divide(BigDecimal.valueOf(product.getSecondaryPerPrimary()), 4, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        movementService.logMovement(
                product, existing, "PURCHASE_TOPUP",
                newSecondary, qtyBefore, qtyBefore + newSecondary,
                scanUnitPricePerSecondary,
                addedByUsername, newInvoice,
                "Top-Up of batch " + existing.getBatchNumber() + " via invoice " + newInvoice);

        // Log offer units top-up movement (if any)
        if (newOffer > 0) {
            movementService.logMovement(
                    product, existing, "OFFER_RECEIVE",
                    newOffer, qtyBefore + newSecondary, qtyBefore + newSecondary + newOffer,
                    BigDecimal.ZERO,
                    addedByUsername, newInvoice,
                    "Free offer units topped-up via invoice " + newInvoice);
        }

        // Log expense for the top-up purchase
        if (req.isLogAsExpense() && newSecondary > 0) {
            BigDecimal gstRate = latestGst.divide(java.math.BigDecimal.valueOf(100));
            BigDecimal buyWithTax = req.getBuyPriceWithoutTax().multiply(java.math.BigDecimal.ONE.add(gstRate))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal cost = buyWithTax.multiply(java.math.BigDecimal.valueOf(req.getPrimaryReceived()));
            if (cost.compareTo(java.math.BigDecimal.ZERO) > 0) {
                com.shop.modules.user.User user = null;
                if (addedByUsername != null && !addedByUsername.equals("System")) {
                    user = userRepository.findByPhone(addedByUsername).orElse(null);
                }
                String desc = String.format(
                    "Stock Top-Up: %d %s of %s from %s (Batch: %s, Invoice: %s)",
                    req.getPrimaryReceived(),
                    product.getPrimaryUnit() != null ? product.getPrimaryUnit() : "BOX",
                    product.getName(),
                    req.getSupplierName(),
                    existing.getBatchNumber(),
                    newInvoice);
                expenseRepository.save(Expense.builder()
                        .category(ExpenseCategory.STOCK_PURCHASE)
                        .amount(cost)
                        .description(desc)
                        .expenseDate(LocalDate.now())
                        .addedBy(user)
                        .build());
            }
        }

        return existing;
    }
}

