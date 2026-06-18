package com.shop.modules.stock;

import com.shop.modules.product.Product;
import com.shop.modules.product.ProductRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.shop.modules.user.UserRepository;
import com.shop.modules.damage.DamageLogRepository;
import com.shop.modules.damage.DamageLog;
import com.shop.modules.damage.DamageReason;
import com.shop.modules.damage.UnitLevel;
import com.shop.modules.damage.ClaimStatus;
import com.shop.modules.expense.Expense;
import com.shop.modules.expense.ExpenseCategory;
import com.shop.modules.expense.ExpenseRepository;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final StockBatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final StockAdjustmentLogRepository stockAdjustmentLogRepository;
    private final UserRepository userRepository;
    private final DamageLogRepository damageLogRepository;
    private final ExpenseRepository expenseRepository;

    // ── Get all stock (non-paginated, for backward compat) ──
    public List<Stock> getAllStock() {
        return stockRepository.findAll();
    }

    // ── Get all stock paginated ──
    public Page<Stock> getAllStockPaged(int page, int size) {
        return stockRepository.findAll(PageRequest.of(page, size, Sort.by("lastUpdated").descending()));
    }

    // ── GetOrCreateStock ──
    @Transactional
    public Stock getOrCreateStock(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

        Stock stock = stockRepository.findByProductIdWithLock(productId).orElse(null);
        if (stock == null) {
            stock = Stock.builder()
                    .product(product)
                    .totalPrimaryUnits(0)
                    .totalSecondaryUnits(0)
                    .openPrimaryRemaining(0)
                    .hasOpenPrimary(false)
                    .lastUpdated(java.time.LocalDateTime.now())
                    .build();
        }

        // Always sync with active batches if any exist
        List<StockBatch> batches = batchRepository.findByProductId(productId);
        if (batches != null && !batches.isEmpty()) {
            int totalSecondary = batches.stream()
                    .filter(b -> b.getExhausted() == null || !b.getExhausted())
                    .mapToInt(b -> b.getSecondaryRemaining() != null ? b.getSecondaryRemaining() : 0)
                    .sum();

            stock.setTotalSecondaryUnits(totalSecondary);
        }
        normalizeStock(stock, product);

        stockRepository.save(stock);
        return stock;
    }

    // ── Get stock by product ──
    public Stock getStockByProduct(UUID productId) {
        return getOrCreateStock(productId);
    }

    // ── Get batch by ID ──
    public StockBatch getBatchById(UUID batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found: " + batchId));
    }

    // ── Get batches FIFO ──
    public List<StockBatch> getBatchesByProduct(
            UUID productId) {
        return batchRepository
                .findActiveBatchesFIFO(productId);
    }

    // ── Get expiring soon ──
    public List<StockBatch> getExpiringSoon() {
        return batchRepository.findExpiringBefore(
                LocalDate.now().plusDays(7));
    }

    // ── Receive stock ──
    @Transactional
    public StockBatch receiveStock(
            ReceiveStockRequest req, String addedByUsername) {

        Product product = productRepository
                .findById(req.getProductId())
                .orElseThrow(() ->
                        new RuntimeException(
                                "Product not found: "
                                        + req.getProductId()));

        // Validate product unit config
        if (product.getSecondaryPerPrimary() == null
                || product.getSecondaryPerPrimary()
                <= 0) {
            throw new RuntimeException(
                    "Product unit config missing — "
                             + "set secondaryPerPrimary first");
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

        // Optional selling price updates
        if (req.getSellPricePrimary() != null && req.getSellPricePrimary().compareTo(BigDecimal.ZERO) > 0) {
            product.setSellPricePrimary(req.getSellPricePrimary());
        }
        if (req.getSellPriceSecondary() != null && req.getSellPriceSecondary().compareTo(BigDecimal.ZERO) > 0) {
            product.setSellPriceSecondary(req.getSellPriceSecondary());
        }
        productRepository.save(product);

        // Calculate secondary from primary
        int secondaryFromPrimary =
                req.getPrimaryReceived()
                        * product.getSecondaryPerPrimary();

        int totalSecondary = secondaryFromPrimary
                + req.getExtraSecondaryReceived();

        // Calculate buy price with tax
        BigDecimal gstPercent = req.getGstPercent() != null && req.getGstPercent().compareTo(BigDecimal.ZERO) >= 0
                ? req.getGstPercent()
                : product.getGstPercent();

        BigDecimal gstRate = gstPercent
                .divide(BigDecimal.valueOf(100));

        BigDecimal taxAmount =
                req.getBuyPriceWithoutTax()
                        .multiply(gstRate)
                        .setScale(2, RoundingMode.HALF_UP);

        BigDecimal buyPriceWithTax =
                req.getBuyPriceWithoutTax()
                        .add(taxAmount);

        // Offer units from distributor (free, cost = ₹0)
        int offerUnits = req.getOfferSecondaryReceived();

        // Create batch
        StockBatch batch = StockBatch.builder()
                .product(product)
                .batchNumber(req.getBatchNumber())
                .invoiceNumber(req.getInvoiceNumber())
                .primaryReceived(req.getPrimaryReceived())
                .secondaryReceived(totalSecondary)
                .secondaryRemaining(totalSecondary)
                .offerSecondaryReceived(offerUnits)
                .offerSecondaryRemaining(offerUnits)
                .buyPriceWithoutTax(
                        req.getBuyPriceWithoutTax())
                .buyPriceWithTax(buyPriceWithTax)
                .gstPercent(gstPercent)
                .expiryDate(req.getExpiryDate())
                .supplierName(req.getSupplierName())
                .exhausted(false)
                .build();

        batchRepository.save(batch);

        // Update stock — include offer units in physical count
        Stock stock = stockRepository
                .findByProductId(product.getId())
                .orElse(Stock.builder()
                        .product(product)
                        .totalPrimaryUnits(0)
                        .totalSecondaryUnits(0)
                        .openPrimaryRemaining(0)
                        .hasOpenPrimary(false)
                        .build());

        // Physical stock = billed units + offer units (both are on shelf)
        stock.setTotalSecondaryUnits(
                stock.getTotalSecondaryUnits()
                        + totalSecondary + offerUnits);

        normalizeStock(stock, product);

        stockRepository.save(stock);

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
                if (req.getBatchNumber() != null && 
                    (req.getBatchNumber().toUpperCase().contains("OPENING") || 
                     req.getBatchNumber().toUpperCase().contains("INITIAL"))) {
                    category = ExpenseCategory.OPENING_STOCK;
                    desc = String.format("Opening Stock: %s of %s (Batch: %s)", 
                            qtyDesc,
                            product.getName(), 
                            req.getBatchNumber());
                } else {
                    desc = String.format("Stock Purchase: %s of %s from %s (Batch: %s)", 
                            qtyDesc,
                            product.getName(), 
                            req.getSupplierName(), 
                            req.getBatchNumber());
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

    // ── Deduct offer units (free) from batch ──
    // Called when billing screen user clicks "Add Offer to Bill"
    @Transactional
    public void deductOfferUnits(UUID batchId, int quantity) {
        StockBatch batch = getBatchById(batchId);
        int available = batch.getOfferSecondaryRemaining() != null ? batch.getOfferSecondaryRemaining() : 0;
        if (available < quantity) {
            throw new RuntimeException(
                "Insufficient offer units in batch " + batch.getBatchNumber()
                + " | Available: " + available + " | Requested: " + quantity);
        }
        batch.setOfferSecondaryRemaining(available - quantity);
        batchRepository.save(batch);

        // Deduct from overall physical stock
        Stock stock = getOrCreateStock(batch.getProduct().getId());
        stock.setTotalSecondaryUnits(Math.max(0, stock.getTotalSecondaryUnits() - quantity));
        normalizeStock(stock, batch.getProduct());
        stockRepository.save(stock);
    }

    @Transactional
    public void addBackOfferStock(UUID productId, UUID batchId, int quantity) {
        StockBatch batch = getBatchById(batchId);
        int current = batch.getOfferSecondaryRemaining() != null ? batch.getOfferSecondaryRemaining() : 0;
        batch.setOfferSecondaryRemaining(current + quantity);
        batchRepository.save(batch);

        Stock stock = getOrCreateStock(productId);
        stock.setTotalSecondaryUnits(stock.getTotalSecondaryUnits() + quantity);
        normalizeStock(stock, batch.getProduct());
        stockRepository.save(stock);
    }

    // ── Deduct by PRIMARY unit (BOX/CRATE/CARTON) ──
    @Transactional(rollbackFor = RuntimeException.class)
    public void deductByPrimary(UUID productId, int quantity) {
        deductByPrimary(productId, quantity, null);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public void deductByPrimary(
            UUID productId, int quantity, UUID batchId) {

        Product product = productRepository
                .findById(productId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Product not found"));

        Stock stock = getOrCreateStock(productId);

        int secondaryPerPrimary =
                product.getSecondaryPerPrimary();

        // Check enough primary units
        if (stock.getTotalPrimaryUnits() < quantity) {
            throw new RuntimeException(
                    "Insufficient "
                            + product.getPrimaryUnit()
                            + " for: " + product.getName()
                            + " | Available: "
                            + stock.getTotalPrimaryUnits()
                            + " | Requested: " + quantity);
        }

        int secondaryToDeduct =
                quantity * secondaryPerPrimary;

        // Deduct total secondary
        stock.setTotalSecondaryUnits(
                stock.getTotalSecondaryUnits() - secondaryToDeduct);

        // Normalize
        normalizeStock(stock, product);

        stockRepository.save(stock);

        // Low stock warning logger
        if (stock.getTotalSecondaryUnits() < product.getLowStockAlert()) {
            System.out.println("[LOW STOCK ALERT] Product '" + product.getName() 
                    + "' is running low. Current: " + stock.getTotalSecondaryUnits() 
                    + ", Threshold: " + product.getLowStockAlert());
        }

        // Deduct from batches
        deductFromBatches(productId,
                secondaryToDeduct, batchId);
    }

    // ── Deduct by SECONDARY unit (LADI/BOTTLE) ──
    @Transactional(rollbackFor = RuntimeException.class)
    public void deductBySecondary(UUID productId, int quantity) {
        deductBySecondary(productId, quantity, null);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public void deductBySecondary(
            UUID productId, int quantity, UUID batchId) {

        Product product = productRepository
                .findById(productId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Product not found"));

        Stock stock = getOrCreateStock(productId);

        // Check enough secondary units
        if (stock.getTotalSecondaryUnits() < quantity) {
            throw new RuntimeException(
                    "Insufficient "
                            + product.getSecondaryUnit()
                            + " for: " + product.getName()
                            + " | Available: "
                            + stock.getTotalSecondaryUnits()
                            + " | Requested: " + quantity);
        }

        // Deduct total secondary
        stock.setTotalSecondaryUnits(
                stock.getTotalSecondaryUnits() - quantity);

        // Normalize
        normalizeStock(stock, product);

        stockRepository.save(stock);

        // Low stock warning logger
        if (stock.getTotalSecondaryUnits() < product.getLowStockAlert()) {
            System.out.println("[LOW STOCK ALERT] Product '" + product.getName() 
                    + "' is running low. Current: " + stock.getTotalSecondaryUnits() 
                    + ", Threshold: " + product.getLowStockAlert());
        }

        // Deduct from batches
        deductFromBatches(productId, quantity, batchId);
    }

    // ── Add stock back (cancel/return/damage) ──
    @Transactional
    public void addBackStock(
            UUID productId,
            int primaryQty,
            int secondaryQty) {

        Stock stock = getOrCreateStock(productId);

        // Use totalSecondaryUnits as the absolute source of truth
        stock.setTotalSecondaryUnits(
                stock.getTotalSecondaryUnits() + secondaryQty);

        normalizeStock(stock, stock.getProduct());

        stockRepository.save(stock);
    }

    // ── Add stock back to a specific batch ──
    @Transactional
    public void addBackStockToSpecificBatch(UUID batchId, int secondaryQty) {
        StockBatch batch = getBatchById(batchId);
        batch.setSecondaryRemaining(batch.getSecondaryRemaining() + secondaryQty);
        if (batch.getSecondaryRemaining() > 0) {
            batch.setExhausted(false);
        }
        batchRepository.save(batch);
    }

    // ── LIFO Batch Restoration on Cancellation ──
    @Transactional
    public void restoreStockToBatches(UUID productId, int secondaryQty) {
        List<StockBatch> batches =
                batchRepository.findByProductIdOrderByReceivedAtDesc(productId);

        int remainingToRestore = secondaryQty;
        for (StockBatch batch : batches) {
            if (remainingToRestore <= 0) break;

            int capacity = batch.getSecondaryReceived() - batch.getSecondaryRemaining();
            if (capacity > 0) {
                int restoreAmt = Math.min(remainingToRestore, capacity);
                batch.setSecondaryRemaining(batch.getSecondaryRemaining() + restoreAmt);
                if (batch.getSecondaryRemaining() > 0) {
                    batch.setExhausted(false);
                }
                batchRepository.save(batch);
                remainingToRestore -= restoreAmt;
            }
        }
    }

    // ── Stock Normalization Helper ──
    private void normalizeStock(Stock stock, Product product) {
        if (product == null || product.getSecondaryPerPrimary() == null || product.getSecondaryPerPrimary() <= 0) {
            return;
        }
        int secondaryPerPrimary = product.getSecondaryPerPrimary();
        stock.setTotalPrimaryUnits(stock.getTotalSecondaryUnits() / secondaryPerPrimary);
        stock.setOpenPrimaryRemaining(stock.getTotalSecondaryUnits() % secondaryPerPrimary);
        stock.setHasOpenPrimary(stock.getOpenPrimaryRemaining() > 0);
    }

    // ── FIFO or Specific Batch deduction ──
    private void deductFromBatches(
            UUID productId, int secondaryQty, UUID batchId) {

        if (batchId != null) {
            StockBatch batch = getBatchById(batchId);
            if (batch.getSecondaryRemaining() < secondaryQty) {
                throw new RuntimeException("Insufficient stock in batch " + batch.getBatchNumber());
            }
            batch.setSecondaryRemaining(batch.getSecondaryRemaining() - secondaryQty);
            if (batch.getSecondaryRemaining() == 0) {
                batch.setExhausted(true);
            }
            batchRepository.save(batch);
            return;
        }

        List<StockBatch> batches =
                batchRepository
                        .findActiveBatchesFIFO(productId);

        int remaining = secondaryQty;

        for (StockBatch batch : batches) {
            if (remaining <= 0) break;

            int deduct = Math.min(
                    batch.getSecondaryRemaining(),
                    remaining);

            batch.setSecondaryRemaining(
                    batch.getSecondaryRemaining()
                            - deduct);

            if (batch.getSecondaryRemaining() == 0) {
                batch.setExhausted(true);
            }

            batchRepository.save(batch);
            remaining -= deduct;
        }
    }

    // ── Adjust stock quantity and log it (Admin/Manager only) ──
    @Transactional(rollbackFor = Exception.class)
    public void adjustStock(UUID batchId, int newSecondaryRemaining, java.math.BigDecimal newBuyPriceWithoutTax, String reason, String adjustedBy) {
        StockBatch batch = getBatchById(batchId);
        int oldSecondaryRemaining = batch.getSecondaryRemaining();
        int change = newSecondaryRemaining - oldSecondaryRemaining;

        // Update batch remaining quantity
        batch.setSecondaryRemaining(newSecondaryRemaining);
        batch.setExhausted(newSecondaryRemaining == 0);

        // Update Buy Price if provided
        java.math.BigDecimal oldBuyPrice = batch.getBuyPriceWithoutTax();
        if (newBuyPriceWithoutTax != null) {
            batch.setBuyPriceWithoutTax(newBuyPriceWithoutTax);
            
            // Recalculate buy price with tax
            Product product = batch.getProduct();
            if (product != null) {
                java.math.BigDecimal gstRate = product.getGstPercent()
                        .divide(java.math.BigDecimal.valueOf(100));
                
                java.math.BigDecimal taxAmount = newBuyPriceWithoutTax
                        .multiply(gstRate)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                
                batch.setBuyPriceWithTax(newBuyPriceWithoutTax.add(taxAmount));
            }
        }
        batchRepository.save(batch);

        // Update product overall stock
        Product product = batch.getProduct();
        if (product != null) {
            Stock stock = getOrCreateStock(product.getId());
            
            stock.setTotalSecondaryUnits(stock.getTotalSecondaryUnits() + change);
            normalizeStock(stock, product);
            stockRepository.save(stock);
        }

        // Build clear, audit-friendly reason string
        StringBuilder logReason = new StringBuilder(reason != null ? reason.trim() : "");
        int ratio = product != null && product.getSecondaryPerPrimary() != null ? product.getSecondaryPerPrimary() : 1;
        String primaryUnit = product != null && product.getPrimaryUnit() != null ? product.getPrimaryUnit() : "BOX";

        if (newSecondaryRemaining != oldSecondaryRemaining) {
            int oldPrimary = oldSecondaryRemaining / ratio;
            int newPrimary = newSecondaryRemaining / ratio;
            logReason.append(" [Quantity updated from ")
                    .append(oldPrimary).append(" ").append(primaryUnit)
                    .append(" to ")
                    .append(newPrimary).append(" ").append(primaryUnit).append("]");
        }

        boolean priceChanged = newBuyPriceWithoutTax != null &&
                (oldBuyPrice == null || newBuyPriceWithoutTax.compareTo(oldBuyPrice) != 0);
        if (priceChanged) {
            logReason.append(" [Buy Price updated from ₹")
                    .append(oldBuyPrice != null ? oldBuyPrice : "0")
                    .append(" to ₹")
                    .append(newBuyPriceWithoutTax).append("]");
        }

        // Look up user's display name if adjustedBy is a phone number
        String adjustedByName = adjustedBy;
        if (userRepository != null && adjustedBy != null && !adjustedBy.equals("System")) {
            adjustedByName = userRepository.findByPhone(adjustedBy)
                    .map(com.shop.modules.user.User::getName)
                    .orElse(adjustedBy);
        }

        // Save adjustment audit log
        StockAdjustmentLog log = StockAdjustmentLog.builder()
                .batchId(batchId)
                .batchNumber(batch.getBatchNumber())
                .productName(product != null ? product.getName() : "Unknown")
                .oldSecondaryRemaining(oldSecondaryRemaining)
                .newSecondaryRemaining(newSecondaryRemaining)
                .adjustedBy(adjustedByName)
                .reason(logReason.toString())
                .timestamp(java.time.LocalDateTime.now())
                .build();

        stockAdjustmentLogRepository.save(log);
    }

    // ── Retrieve adjustment log (Admin only) — non-paginated ──
    public List<StockAdjustmentLog> getAdjustmentLogs() {
        return stockAdjustmentLogRepository.findAllByOrderByTimestampDesc();
    }

    // ── Retrieve adjustment log paginated ──
    public Page<StockAdjustmentLog> getAdjustmentLogsPaged(int page, int size) {
        return stockAdjustmentLogRepository.findAllByOrderByTimestampDesc(
                PageRequest.of(page, size));
    }

    // ── Write off expired stock batch to Damage Log ──
    @Transactional(rollbackFor = Exception.class)
    public void writeOffExpiredBatch(UUID batchId, String adjustedBy) {
        StockBatch batch = getBatchById(batchId);
        if (batch.getExhausted() != null && batch.getExhausted()) {
            throw new RuntimeException("Batch is already exhausted");
        }

        int qty = batch.getSecondaryRemaining();
        if (qty <= 0) {
            batch.setExhausted(true);
            batchRepository.save(batch);
            return;
        }

        Product product = batch.getProduct();
        
        // Determine unit type to log in damage log
        com.shop.modules.product.UnitType unitType = com.shop.modules.product.UnitType.SINGLE;
        if (product.getSecondaryUnit() != null) {
            try {
                unitType = com.shop.modules.product.UnitType.valueOf(product.getSecondaryUnit().toUpperCase());
            } catch (Exception e) {
                // fallback
            }
        }

        // Calculate value loss
        BigDecimal buyPricePerSecondary = product.getBuyPricePerSecondary();
        if (buyPricePerSecondary == null || buyPricePerSecondary.compareTo(BigDecimal.ZERO) == 0) {
            buyPricePerSecondary = batch.getBuyPricePerSecondary(product.getSecondaryPerPrimary());
        }
        BigDecimal valueLoss = buyPricePerSecondary.multiply(BigDecimal.valueOf(qty));

        // Get user for logger
        com.shop.modules.user.User user = null;
        if (adjustedBy != null && !adjustedBy.equals("System")) {
            user = userRepository.findByPhone(adjustedBy).orElse(null);
        }

        // Create damage log
        DamageLog damage = DamageLog.builder()
                .product(product)
                .batch(batch)
                .unitType(unitType)
                .unitLevel(UnitLevel.SECONDARY)
                .claimStatus(ClaimStatus.NON_CLAIMABLE)
                .quantity(qty)
                .reason(DamageReason.EXPIRE)
                .valueLoss(valueLoss)
                .notes("Stock expired. Write-off logged. Expiry date: " + batch.getExpiryDate() + " (Batch: " + batch.getBatchNumber() + ")")
                .loggedBy(user)
                .build();

        damageLogRepository.save(damage);

        // Adjust batch remaining to 0
        batch.setSecondaryRemaining(0);
        batch.setExhausted(true);
        batchRepository.save(batch);

        // Decrease overall stock
        Stock stock = getOrCreateStock(product.getId());
        stock.setTotalSecondaryUnits(Math.max(0, stock.getTotalSecondaryUnits() - qty));
        normalizeStock(stock, product);
        stockRepository.save(stock);

        // Save adjustment audit log
        StockAdjustmentLog log = StockAdjustmentLog.builder()
                .batchId(batchId)
                .batchNumber(batch.getBatchNumber())
                .productName(product.getName())
                .oldSecondaryRemaining(qty)
                .newSecondaryRemaining(0)
                .adjustedBy(user != null ? user.getName() : adjustedBy)
                .reason("Expired stock written off. Expiry: " + batch.getExpiryDate())
                .timestamp(java.time.LocalDateTime.now())
                .build();

        stockAdjustmentLogRepository.save(log);
    }

    public List<StockBatch> getBatchesByDate(LocalDate date) {
        java.time.LocalDateTime start = date.atStartOfDay();
        java.time.LocalDateTime end = date.plusDays(1).atStartOfDay();
        return batchRepository.findByReceivedAtBetweenOrderByReceivedAtDesc(start, end);
    }

    public Page<StockBatch> getRecentBatchesPaged(int page, int size) {
        return batchRepository.findAll(
                org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("receivedAt").descending())
        );
    }

    public List<StockBatch> getBatchesByInvoice(String invoiceNumber) {
        if (invoiceNumber == null || invoiceNumber.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return batchRepository.findByInvoiceNumberIgnoreCase(invoiceNumber.trim());
    }

    // ── Inner request class ──
    @Data
    public static class ReceiveStockRequest {
        private UUID productId;
        private String batchNumber;
        private String invoiceNumber;
        private int primaryReceived;
        private int extraSecondaryReceived;
        private int offerSecondaryReceived = 0; // free units from distributor
        private BigDecimal buyPriceWithoutTax;
        private BigDecimal gstPercent;
        private LocalDate expiryDate;
        private String supplierName;
        private BigDecimal sellPricePrimary;
        private BigDecimal sellPriceSecondary;
        private boolean logAsExpense;
    }
}