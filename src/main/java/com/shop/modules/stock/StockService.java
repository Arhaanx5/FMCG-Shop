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
import java.time.LocalDateTime;
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

    // Modular Services Injection
    private final StockReceiveService receiveService;
    private final StockMovementService movementService;
    private final StockInventoryService inventoryService;

    public List<Stock> getAllStock() {
        return stockRepository.findAll();
    }

    public Page<Stock> getAllStockPaged(int page, int size) {
        return stockRepository.findAll(PageRequest.of(page, size, Sort.by("lastUpdated").descending()));
    }

    @Transactional
    public Stock getOrCreateStock(UUID productId) {
        return inventoryService.getOrCreateStock(productId);
    }

    public Stock getStockByProduct(UUID productId) {
        return inventoryService.getOrCreateStock(productId);
    }

    public StockBatch getBatchById(UUID batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found: " + batchId));
    }

    public List<StockBatch> getBatchesByProduct(UUID productId) {
        return batchRepository.findActiveBatchesFIFO(productId);
    }

    public List<StockBatch> getExpiringSoon() {
        return batchRepository.findExpiringBefore(LocalDate.now().plusDays(7));
    }

    @Transactional
    public StockBatch receiveStock(ReceiveStockRequest req, String addedByUsername) {
        return receiveService.receiveStock(req, addedByUsername);
    }

    @Transactional
    public void deductOfferUnits(UUID batchId, int quantity) {
        // Pessimistic lock batch
        StockBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found: " + batchId));

        int available = batch.getOfferSecondaryRemaining() != null ? batch.getOfferSecondaryRemaining() : 0;
        if (available < quantity) {
            throw new RuntimeException("Insufficient offer units in batch " + batch.getBatchNumber()
                    + " | Available: " + available + " | Requested: " + quantity);
        }

        Stock stock = inventoryService.getOrCreateStock(batch.getProduct().getId());
        int qtyBefore = stock.getTotalSecondaryUnits();
        int qtyAfter = qtyBefore - quantity;

        batch.setOfferSecondaryRemaining(available - quantity);
        batchRepository.save(batch);

        stock.setTotalSecondaryUnits(Math.max(0, qtyAfter));
        inventoryService.normalizeStock(stock, batch.getProduct());
        stockRepository.save(stock);

        movementService.logMovementAsync(
                batch.getProduct(),
                batch,
                "SALE",
                -quantity,
                qtyBefore,
                qtyAfter,
                BigDecimal.ZERO,
                "System",
                batch.getInvoiceNumber(),
                "Deducted offer units"
        );
    }

    @Transactional
    public void addBackOfferStock(UUID productId, UUID batchId, int quantity) {
        StockBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found: " + batchId));

        int current = batch.getOfferSecondaryRemaining() != null ? batch.getOfferSecondaryRemaining() : 0;
        batch.setOfferSecondaryRemaining(current + quantity);
        batchRepository.save(batch);

        Stock stock = inventoryService.getOrCreateStock(productId);
        int qtyBefore = stock.getTotalSecondaryUnits();
        int qtyAfter = qtyBefore + quantity;

        stock.setTotalSecondaryUnits(qtyAfter);
        inventoryService.normalizeStock(stock, batch.getProduct());
        stockRepository.save(stock);

        movementService.logMovementAsync(
                batch.getProduct(),
                batch,
                "RETURN_IN",
                quantity,
                qtyBefore,
                qtyAfter,
                BigDecimal.ZERO,
                "System",
                batch.getInvoiceNumber(),
                "Restored offer units"
        );
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public void deductByPrimary(UUID productId, int quantity) {
        deductByPrimary(productId, quantity, null);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public void deductByPrimary(UUID productId, int quantity, UUID batchId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        Stock stock = inventoryService.getOrCreateStock(productId);
        int secondaryPerPrimary = product.getSecondaryPerPrimary();

        if (stock.getTotalPrimaryUnits() < quantity) {
            throw new RuntimeException("Insufficient " + product.getPrimaryUnit() + " for: " + product.getName()
                    + " | Available: " + stock.getTotalPrimaryUnits() + " | Requested: " + quantity);
        }

        int secondaryToDeduct = quantity * secondaryPerPrimary;
        int qtyBefore = stock.getTotalSecondaryUnits();
        int qtyAfter = qtyBefore - secondaryToDeduct;

        stock.setTotalSecondaryUnits(qtyAfter);
        inventoryService.normalizeStock(stock, product);
        stockRepository.save(stock);

        if (qtyAfter < product.getLowStockAlertInSecondary()) {
            System.out.println("[LOW STOCK ALERT] Product '" + product.getName() + "' is running low.");
        }

        deductFromBatches(productId, secondaryToDeduct, batchId);

        BigDecimal price = product.getBuyPricePerSecondary();
        movementService.logMovementAsync(
                product,
                batchId != null ? getBatchById(batchId) : null,
                "SALE",
                -secondaryToDeduct,
                qtyBefore,
                qtyAfter,
                price,
                "System",
                null,
                "Deducted primary units"
        );
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public void deductBySecondary(UUID productId, int quantity) {
        deductBySecondary(productId, quantity, null);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public void deductBySecondary(UUID productId, int quantity, UUID batchId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        Stock stock = inventoryService.getOrCreateStock(productId);
        int qtyBefore = stock.getTotalSecondaryUnits();

        if (qtyBefore < quantity) {
            throw new RuntimeException("Insufficient " + product.getSecondaryUnit() + " for: " + product.getName()
                    + " | Available: " + qtyBefore + " | Requested: " + quantity);
        }

        int qtyAfter = qtyBefore - quantity;
        stock.setTotalSecondaryUnits(qtyAfter);
        inventoryService.normalizeStock(stock, product);
        stockRepository.save(stock);

        if (qtyAfter < product.getLowStockAlertInSecondary()) {
            System.out.println("[LOW STOCK ALERT] Product '" + product.getName() + "' is running low.");
        }

        deductFromBatches(productId, quantity, batchId);

        BigDecimal price = product.getBuyPricePerSecondary();
        movementService.logMovementAsync(
                product,
                batchId != null ? getBatchById(batchId) : null,
                "SALE",
                -quantity,
                qtyBefore,
                qtyAfter,
                price,
                "System",
                null,
                "Deducted secondary units"
        );
    }

    @Transactional
    public void addBackStock(UUID productId, int primaryQty, int secondaryQty) {
        Stock stock = inventoryService.getOrCreateStock(productId);
        int qtyBefore = stock.getTotalSecondaryUnits();
        int qtyAfter = qtyBefore + secondaryQty;

        stock.setTotalSecondaryUnits(qtyAfter);
        inventoryService.normalizeStock(stock, stock.getProduct());
        stockRepository.save(stock);

        BigDecimal price = stock.getProduct().getBuyPricePerSecondary();
        movementService.logMovementAsync(
                stock.getProduct(),
                null,
                "RETURN_IN",
                secondaryQty,
                qtyBefore,
                qtyAfter,
                price,
                "System",
                null,
                "Restored general inventory stock"
        );
    }

    @Transactional
    public void addBackStockToSpecificBatch(UUID batchId, int secondaryQty) {
        StockBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found: " + batchId));

        int oldRemaining = batch.getSecondaryRemaining();
        batch.setSecondaryRemaining(oldRemaining + secondaryQty);
        if (batch.getSecondaryRemaining() > 0) {
            batch.setExhausted(false);
            if (batch.getBatchStatus() == BatchStatus.WRITTEN_OFF) {
                batch.setBatchStatus(BatchStatus.ACTIVE);
            }
        }
        batchRepository.save(batch);

        movementService.logMovementAsync(
                batch.getProduct(),
                batch,
                "RETURN_IN",
                secondaryQty,
                null,
                null,
                batch.getBuyPricePerSecondary(batch.getProduct().getSecondaryPerPrimary()),
                "System",
                batch.getInvoiceNumber(),
                "Restored specific batch stock"
        );
    }

    @Transactional
    public void restoreStockToBatches(UUID productId, int secondaryQty) {
        List<StockBatch> batches = batchRepository.findByProductIdOrderByReceivedAtDesc(productId);

        int remainingToRestore = secondaryQty;
        for (StockBatch batch : batches) {
            if (remainingToRestore <= 0) break;

            // Lock batch
            StockBatch lockedBatch = batchRepository.findByIdForUpdate(batch.getId()).orElse(batch);

            int capacity = lockedBatch.getSecondaryReceived() - lockedBatch.getSecondaryRemaining();
            if (capacity > 0) {
                int restoreAmt = Math.min(remainingToRestore, capacity);
                lockedBatch.setSecondaryRemaining(lockedBatch.getSecondaryRemaining() + restoreAmt);
                if (lockedBatch.getSecondaryRemaining() > 0) {
                    lockedBatch.setExhausted(false);
                    if (lockedBatch.getBatchStatus() == BatchStatus.WRITTEN_OFF) {
                        lockedBatch.setBatchStatus(BatchStatus.ACTIVE);
                    }
                }
                batchRepository.save(lockedBatch);
                remainingToRestore -= restoreAmt;

                movementService.logMovementAsync(
                        lockedBatch.getProduct(),
                        lockedBatch,
                        "RETURN_IN",
                        restoreAmt,
                        null,
                        null,
                        lockedBatch.getBuyPricePerSecondary(lockedBatch.getProduct().getSecondaryPerPrimary()),
                        "System",
                        lockedBatch.getInvoiceNumber(),
                        "LIFO batch restoration"
                );
            }
        }
    }

    private void deductFromBatches(UUID productId, int secondaryQty, UUID batchId) {
        if (batchId != null) {
            StockBatch batch = batchRepository.findByIdForUpdate(batchId)
                    .orElseThrow(() -> new RuntimeException("Batch not found: " + batchId));
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

        List<StockBatch> batches = batchRepository.findActiveBatchesFIFO(productId);
        int remaining = secondaryQty;

        for (StockBatch batch : batches) {
            if (remaining <= 0) break;

            StockBatch lockedBatch = batchRepository.findByIdForUpdate(batch.getId()).orElse(batch);
            int deduct = Math.min(lockedBatch.getSecondaryRemaining(), remaining);
            lockedBatch.setSecondaryRemaining(lockedBatch.getSecondaryRemaining() - deduct);

            if (lockedBatch.getSecondaryRemaining() == 0) {
                lockedBatch.setExhausted(true);
            }

            batchRepository.save(lockedBatch);
            remaining -= deduct;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void adjustStock(UUID batchId, int newSecondaryRemaining, Integer newOfferSecondaryRemaining, BigDecimal newBuyPriceWithoutTax, String reason, String adjustedBy) {
        inventoryService.adjustStock(batchId, newSecondaryRemaining, newOfferSecondaryRemaining, newBuyPriceWithoutTax, reason, adjustedBy);
    }

    public List<StockAdjustmentLog> getAdjustmentLogs() {
        return stockAdjustmentLogRepository.findAllByOrderByTimestampDesc();
    }

    public Page<StockAdjustmentLog> getAdjustmentLogsPaged(int page, int size) {
        return stockAdjustmentLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(page, size));
    }

    @Transactional(rollbackFor = Exception.class)
    public void writeOffExpiredBatch(UUID batchId, String adjustedBy) {
        StockBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found: " + batchId));

        if (batch.getExhausted() != null && batch.getExhausted()) {
            throw new RuntimeException("Batch is already exhausted");
        }

        int qty = batch.getSecondaryRemaining();
        if (qty <= 0) {
            batch.setExhausted(true);
            batch.setBatchStatus(BatchStatus.WRITTEN_OFF);
            batchRepository.save(batch);
            return;
        }

        Product product = batch.getProduct();
        com.shop.modules.product.UnitType unitType = com.shop.modules.product.UnitType.SINGLE;
        if (product.getSecondaryUnit() != null) {
            try {
                unitType = com.shop.modules.product.UnitType.valueOf(product.getSecondaryUnit().toUpperCase());
            } catch (Exception ignored) {}
        }

        BigDecimal buyPricePerSecondary = product.getBuyPricePerSecondary();
        if (buyPricePerSecondary == null || buyPricePerSecondary.compareTo(BigDecimal.ZERO) == 0) {
            buyPricePerSecondary = batch.getBuyPricePerSecondary(product.getSecondaryPerPrimary());
        }
        BigDecimal valueLoss = buyPricePerSecondary.multiply(BigDecimal.valueOf(qty));

        com.shop.modules.user.User user = null;
        if (adjustedBy != null && !adjustedBy.equals("System")) {
            user = userRepository.findByPhone(adjustedBy).orElse(null);
        }

        DamageLog damage = DamageLog.builder()
                .product(product)
                .batch(batch)
                .unitType(unitType)
                .unitLevel(UnitLevel.SECONDARY)
                .claimStatus(ClaimStatus.NON_CLAIMABLE)
                .quantity(qty)
                .reason(DamageReason.EXPIRE)
                .valueLoss(valueLoss)
                .notes("Stock expired. Write-off logged. Expiry: " + batch.getExpiryDate() + " (Batch: " + batch.getBatchNumber() + ")")
                .loggedBy(user)
                .build();
        damageLogRepository.save(damage);

        batch.setSecondaryRemaining(0);
        batch.setExhausted(true);
        batch.setBatchStatus(BatchStatus.WRITTEN_OFF);
        batchRepository.save(batch);

        Stock stock = inventoryService.getOrCreateStock(product.getId());
        int qtyBefore = stock.getTotalSecondaryUnits();
        int qtyAfter = Math.max(0, qtyBefore - qty);
        stock.setTotalSecondaryUnits(qtyAfter);
        inventoryService.normalizeStock(stock, product);
        stockRepository.save(stock);

        StockAdjustmentLog log = StockAdjustmentLog.builder()
                .batchId(batchId)
                .batchNumber(batch.getBatchNumber())
                .productName(product.getName())
                .oldSecondaryRemaining(qty)
                .newSecondaryRemaining(0)
                .adjustedBy(user != null ? user.getName() : adjustedBy)
                .reason("Expired stock written off. Expiry: " + batch.getExpiryDate())
                .timestamp(LocalDateTime.now())
                .build();
        stockAdjustmentLogRepository.save(log);

        movementService.logMovementAsync(
                product,
                batch,
                "EXPIRY",
                -qty,
                qtyBefore,
                qtyAfter,
                buyPricePerSecondary,
                adjustedBy,
                null,
                "Expired write-off"
        );
    }

    public List<StockBatch> getBatchesByDate(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        return batchRepository.findByReceivedAtBetweenOrderByReceivedAtDesc(start, end);
    }

    public Page<StockBatch> getRecentBatchesPaged(int page, int size) {
        return batchRepository.findAll(PageRequest.of(page, size, Sort.by("receivedAt").descending()));
    }

    public List<StockBatch> getBatchesByInvoice(String invoiceNumber) {
        if (invoiceNumber == null || invoiceNumber.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return batchRepository.findByInvoiceNumberIgnoreCase(invoiceNumber.trim());
    }

    @Data
    public static class ReceiveStockRequest {
        private UUID productId;
        private String batchNumber;
        private String invoiceNumber;
        private String supplierInvoiceNumber;
        private LocalDate supplierInvoiceDate;
        private LocalDate stockReceivedDate;
        private LocalDate manufacturingDate;
        private String remarks;
        private int primaryReceived;
        private int extraSecondaryReceived;
        private int offerSecondaryReceived = 0;
        private BigDecimal buyPriceWithoutTax;
        private BigDecimal gstPercent;
        private LocalDate expiryDate;
        private String supplierName;
        private BigDecimal sellPricePrimary;
        private BigDecimal sellPriceSecondary;
        private boolean logAsExpense;
    }
}