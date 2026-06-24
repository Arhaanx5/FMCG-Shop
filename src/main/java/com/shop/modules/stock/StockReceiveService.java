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

        // Check duplicate batch
        if (req.getBatchNumber() != null && !req.getBatchNumber().isBlank()) {
            boolean batchExists = batchRepository.existsByProductIdAndBatchNumberIgnoreCase(product.getId(), req.getBatchNumber().trim());
            if (batchExists) {
                throw new RuntimeException("Batch number '" + req.getBatchNumber().trim() + "' already exists for this product.");
            }
        }

        // Update product master prices
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
                .build();

        batchRepository.save(batch);

        Stock stock = inventoryService.getOrCreateStock(product.getId());
        int qtyBefore = stock.getTotalSecondaryUnits();
        int receivedQty = totalSecondary + offerUnits;
        int qtyAfter = qtyBefore + receivedQty;

        stock.setTotalSecondaryUnits(qtyAfter);
        inventoryService.normalizeStock(stock, product);
        stockRepository.save(stock);

        // Async log stock movement
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
                receivedQty,
                qtyBefore,
                qtyAfter,
                batch.getBuyPricePerSecondary(product.getSecondaryPerPrimary()),
                addedByUsername,
                batch.getInvoiceNumber(),
                req.getRemarks()
        );

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
}
