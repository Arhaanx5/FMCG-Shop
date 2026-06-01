package com.shop.modules.stock;

import com.shop.modules.product.Product;
import com.shop.modules.product.ProductRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.shop.modules.user.UserRepository;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final StockBatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final StockAdjustmentLogRepository stockAdjustmentLogRepository;
    private final UserRepository userRepository;

    // ── Get all stock ──
    public List<Stock> getAllStock() {
        return stockRepository.findAll();
    }

    // ── Get stock by product ──
    public Stock getStockByProduct(UUID productId) {
        return stockRepository
                .findByProductId(productId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "No stock found for product: "
                                        + productId));
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
            ReceiveStockRequest req) {

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

        // Calculate secondary from primary
        int secondaryFromPrimary =
                req.getPrimaryReceived()
                        * product.getSecondaryPerPrimary();

        int totalSecondary = secondaryFromPrimary
                + req.getExtraSecondaryReceived();

        // Calculate buy price with tax
        BigDecimal gstRate = product.getGstPercent()
                .divide(BigDecimal.valueOf(100));

        BigDecimal taxAmount =
                req.getBuyPriceWithoutTax()
                        .multiply(gstRate)
                        .setScale(2, RoundingMode.HALF_UP);

        BigDecimal buyPriceWithTax =
                req.getBuyPriceWithoutTax()
                        .add(taxAmount);

        // Create batch
        StockBatch batch = StockBatch.builder()
                .product(product)
                .batchNumber(req.getBatchNumber())
                .primaryReceived(req.getPrimaryReceived())
                .secondaryReceived(totalSecondary)
                .secondaryRemaining(totalSecondary)
                .buyPriceWithoutTax(
                        req.getBuyPriceWithoutTax())
                .buyPriceWithTax(buyPriceWithTax)
                .gstPercent(product.getGstPercent())
                .expiryDate(req.getExpiryDate())
                .supplierName(req.getSupplierName())
                .exhausted(false)
                .build();

        batchRepository.save(batch);

        // Update stock
        Stock stock = stockRepository
                .findByProductId(product.getId())
                .orElse(Stock.builder()
                        .product(product)
                        .totalPrimaryUnits(0)
                        .totalSecondaryUnits(0)
                        .openPrimaryRemaining(0)
                        .hasOpenPrimary(false)
                        .build());

        stock.setTotalSecondaryUnits(
                stock.getTotalSecondaryUnits()
                        + totalSecondary);

        normalizeStock(stock, product);

        stockRepository.save(stock);
        return batch;
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

        Stock stock = stockRepository
                .findByProductId(productId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "No stock found for: "
                                        + product.getName()));

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

        Stock stock = stockRepository
                .findByProductId(productId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "No stock found for: "
                                        + product.getName()));

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

        Stock stock = stockRepository
                .findByProductId(productId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Stock not found for: "
                                        + productId));

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
            Stock stock = stockRepository.findByProductId(product.getId())
                    .orElseThrow(() -> new RuntimeException("Stock record not found for product: " + product.getName()));
            
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

    // ── Retrieve adjustment log (Admin only) ──
    public List<StockAdjustmentLog> getAdjustmentLogs() {
        return stockAdjustmentLogRepository.findAllByOrderByTimestampDesc();
    }

    // ── Inner request class ──
    @Data
    public static class ReceiveStockRequest {
        private UUID productId;
        private String batchNumber;
        private int primaryReceived;
        private int extraSecondaryReceived;
        private BigDecimal buyPriceWithoutTax;
        private LocalDate expiryDate;
        private String supplierName;
    }
}