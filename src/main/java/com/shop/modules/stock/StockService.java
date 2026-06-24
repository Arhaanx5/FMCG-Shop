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
        deductOfferUnits(batchId, quantity, "System", null, BigDecimal.ZERO, "Deducted offer units");
    }

    @Transactional
    public void deductOfferUnits(UUID batchId, int quantity, String username, String referenceNumber, BigDecimal unitPrice, String remarks) {
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

        movementService.logMovement(
                batch.getProduct(),
                batch,
                "SALE",
                -quantity,
                qtyBefore,
                qtyAfter,
                unitPrice != null ? unitPrice : BigDecimal.ZERO,
                username,
                referenceNumber != null ? referenceNumber : batch.getInvoiceNumber(),
                remarks != null ? remarks : "Deducted offer units"
        );
    }

    @Transactional
    public void addBackOfferStock(UUID productId, UUID batchId, int quantity) {
        addBackOfferStock(productId, batchId, quantity, "System", null, BigDecimal.ZERO, "Restored offer units");
    }

    @Transactional
    public void addBackOfferStock(UUID productId, UUID batchId, int quantity, String username, String referenceNumber, BigDecimal unitPrice, String remarks) {
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

        movementService.logMovement(
                batch.getProduct(),
                batch,
                "RETURN_IN",
                quantity,
                qtyBefore,
                qtyAfter,
                unitPrice != null ? unitPrice : BigDecimal.ZERO,
                username,
                referenceNumber != null ? referenceNumber : batch.getInvoiceNumber(),
                remarks != null ? remarks : "Restored offer units"
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
        deductByPrimary(productId, quantity, batchId, "System", null, product.getBuyPricePerSecondary(), "Deducted primary units");
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public List<com.shop.modules.stock.dto.BatchDeductionRecord> deductByPrimary(UUID productId, int quantity, UUID batchId, String username, String referenceNumber, BigDecimal unitPrice, String remarks) {
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

        List<com.shop.modules.stock.dto.BatchDeductionRecord> deductions = deductFromBatches(productId, secondaryToDeduct, batchId);

        BigDecimal price = unitPrice != null ? unitPrice : product.getBuyPricePerSecondary();
        movementService.logMovement(
                product,
                batchId != null ? getBatchById(batchId) : null,
                "SALE",
                -secondaryToDeduct,
                qtyBefore,
                qtyAfter,
                price,
                username,
                referenceNumber,
                remarks
        );
        return deductions;
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public List<com.shop.modules.stock.dto.BatchDeductionRecord> deductBySecondary(UUID productId, int quantity) {
        return deductBySecondary(productId, quantity, null);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public List<com.shop.modules.stock.dto.BatchDeductionRecord> deductBySecondary(UUID productId, int quantity, UUID batchId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        return deductBySecondary(productId, quantity, batchId, "System", null, product.getBuyPricePerSecondary(), "Deducted secondary units");
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public List<com.shop.modules.stock.dto.BatchDeductionRecord> deductBySecondary(UUID productId, int quantity, UUID batchId, String username, String referenceNumber, BigDecimal unitPrice, String remarks) {
        return deductBySecondary(productId, quantity, batchId, username, referenceNumber, unitPrice, remarks, "SALE");
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public List<com.shop.modules.stock.dto.BatchDeductionRecord> deductBySecondary(UUID productId, int quantity, UUID batchId, String username, String referenceNumber, BigDecimal unitPrice, String remarks, String movementType) {
        if ("RETURN_OUT".equals(movementType) && batchId == null) {
            throw new IllegalArgumentException("Batch selection is required for RETURN_OUT movement type");
        }

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

        List<com.shop.modules.stock.dto.BatchDeductionRecord> deductions = deductFromBatches(productId, quantity, batchId);

        BigDecimal price = unitPrice != null ? unitPrice : product.getBuyPricePerSecondary();
        movementService.logMovement(
                product,
                batchId != null ? getBatchById(batchId) : null,
                movementType != null ? movementType : "SALE",
                -quantity,
                qtyBefore,
                qtyAfter,
                price,
                username,
                referenceNumber,
                remarks
        );
        return deductions;
    }

    @Transactional
    public void addBackStock(UUID productId, int primaryQty, int secondaryQty) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        addBackStock(productId, primaryQty, secondaryQty, "System", null, product.getBuyPricePerSecondary(), "Restored general inventory stock");
    }

    @Transactional
    public void addBackStock(UUID productId, int primaryQty, int secondaryQty, String username, String referenceNumber, BigDecimal unitPrice, String remarks) {
        addBackStock(productId, primaryQty, secondaryQty, username, referenceNumber, unitPrice, remarks, "RETURN_IN");
    }

    @Transactional
    public void addBackStock(UUID productId, int primaryQty, int secondaryQty, String username, String referenceNumber, BigDecimal unitPrice, String remarks, String movementType) {
        Stock stock = inventoryService.getOrCreateStock(productId);
        int qtyBefore = stock.getTotalSecondaryUnits();
        int qtyAfter = qtyBefore + secondaryQty;

        stock.setTotalSecondaryUnits(qtyAfter);
        inventoryService.normalizeStock(stock, stock.getProduct());
        stockRepository.save(stock);

        BigDecimal price = unitPrice != null ? unitPrice : stock.getProduct().getBuyPricePerSecondary();
        movementService.logMovement(
                stock.getProduct(),
                null,
                movementType != null ? movementType : "RETURN_IN",
                secondaryQty,
                qtyBefore,
                qtyAfter,
                price,
                username,
                referenceNumber,
                remarks
        );
    }

    @Transactional
    public void addBackStockToBatch(UUID productId, UUID batchId, int primaryQty, int secondaryQty, String username, String referenceNumber, BigDecimal unitPrice, String remarks) {
        addBackStockToBatch(productId, batchId, primaryQty, secondaryQty, username, referenceNumber, unitPrice, remarks, "RETURN_IN");
    }

    @Transactional
    public void addBackStockToBatch(UUID productId, UUID batchId, int primaryQty, int secondaryQty, String username, String referenceNumber, BigDecimal unitPrice, String remarks, String movementType) {
        Stock stock = inventoryService.getOrCreateStock(productId);
        int qtyBefore = stock.getTotalSecondaryUnits();
        int qtyAfter = qtyBefore + secondaryQty;

        stock.setTotalSecondaryUnits(qtyAfter);
        inventoryService.normalizeStock(stock, stock.getProduct());
        stockRepository.save(stock);

        BigDecimal price = unitPrice != null ? unitPrice : stock.getProduct().getBuyPricePerSecondary();

        if (batchId != null) {
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

            movementService.logMovement(
                    stock.getProduct(),
                    batch,
                    movementType != null ? movementType : "RETURN_IN",
                    secondaryQty,
                    qtyBefore,
                    qtyAfter,
                    price,
                    username,
                    referenceNumber,
                    remarks
            );
        } else {
            // LIFO batch restoration fallback
            List<StockBatch> batches = batchRepository.findByProductIdOrderByReceivedAtDesc(productId);
            int remainingToRestore = secondaryQty;
            for (StockBatch batch : batches) {
                if (remainingToRestore <= 0) break;

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
                }
            }

            movementService.logMovement(
                    stock.getProduct(),
                    null,
                    movementType != null ? movementType : "RETURN_IN",
                    secondaryQty,
                    qtyBefore,
                    qtyAfter,
                    price,
                    username,
                    referenceNumber,
                    remarks
            );
        }
    }

    @Transactional
    public void addBackStockToSpecificBatch(UUID batchId, int secondaryQty) {
        StockBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found: " + batchId));
        addBackStockToSpecificBatch(batchId, secondaryQty, "System", null, batch.getBuyPricePerSecondary(batch.getProduct().getSecondaryPerPrimary()), "Restored specific batch stock");
    }

    @Transactional
    public void addBackStockToSpecificBatch(UUID batchId, int secondaryQty, String username, String referenceNumber, BigDecimal unitPrice, String remarks) {
        addBackStockToSpecificBatch(batchId, secondaryQty, username, referenceNumber, unitPrice, remarks, "RETURN_IN");
    }

    @Transactional
    public void addBackStockToSpecificBatch(UUID batchId, int secondaryQty, String username, String referenceNumber, BigDecimal unitPrice, String remarks, String movementType) {
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

        movementService.logMovement(
                batch.getProduct(),
                batch,
                movementType != null ? movementType : "RETURN_IN",
                secondaryQty,
                null,
                null,
                unitPrice != null ? unitPrice : batch.getBuyPricePerSecondary(batch.getProduct().getSecondaryPerPrimary()),
                username,
                referenceNumber != null ? referenceNumber : batch.getInvoiceNumber(),
                remarks != null ? remarks : "Restored specific batch stock"
        );
    }

    @Transactional
    public void restoreStockToBatches(UUID productId, int secondaryQty) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        restoreStockToBatches(productId, secondaryQty, "System", null, product.getBuyPricePerSecondary(), "LIFO batch restoration");
    }

    @Transactional
    public void restoreStockToBatches(UUID productId, int secondaryQty, String username, String referenceNumber, BigDecimal unitPrice, String remarks) {
        restoreStockToBatches(productId, secondaryQty, username, referenceNumber, unitPrice, remarks, "RETURN_IN");
    }

    @Transactional
    public void restoreStockToBatches(UUID productId, int secondaryQty, String username, String referenceNumber, BigDecimal unitPrice, String remarks, String movementType) {
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

                movementService.logMovement(
                        lockedBatch.getProduct(),
                        lockedBatch,
                        movementType != null ? movementType : "RETURN_IN",
                        restoreAmt,
                        null,
                        null,
                        unitPrice != null ? unitPrice : lockedBatch.getBuyPricePerSecondary(lockedBatch.getProduct().getSecondaryPerPrimary()),
                        username,
                        referenceNumber != null ? referenceNumber : lockedBatch.getInvoiceNumber(),
                        remarks != null ? remarks : "LIFO batch restoration"
                );
            }
        }
    }

    private List<com.shop.modules.stock.dto.BatchDeductionRecord> deductFromBatches(UUID productId, int secondaryQty, UUID batchId) {
        List<com.shop.modules.stock.dto.BatchDeductionRecord> deductions = new java.util.ArrayList<>();
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
            deductions.add(new com.shop.modules.stock.dto.BatchDeductionRecord(batchId, secondaryQty));
            return deductions;
        }

        List<StockBatch> batches = batchRepository.findActiveBatchesFIFO(productId);
        int remaining = secondaryQty;

        for (StockBatch batch : batches) {
            if (remaining <= 0) break;

            StockBatch lockedBatch = batchRepository.findByIdForUpdate(batch.getId()).orElse(batch);
            int deduct = Math.min(lockedBatch.getSecondaryRemaining(), remaining);
            if (deduct > 0) {
                lockedBatch.setSecondaryRemaining(lockedBatch.getSecondaryRemaining() - deduct);

                if (lockedBatch.getSecondaryRemaining() == 0) {
                    lockedBatch.setExhausted(true);
                }

                batchRepository.save(lockedBatch);
                deductions.add(new com.shop.modules.stock.dto.BatchDeductionRecord(lockedBatch.getId(), deduct));
                remaining -= deduct;
            }
        }
        if (remaining > 0) {
            throw new RuntimeException("Insufficient stock to complete deduction: " + remaining + " units missing");
        }
        return deductions;
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

        movementService.logMovement(
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
        return getRecentBatchesPaged(page, size, null);
    }

    public Page<StockBatch> getRecentBatchesPaged(int page, int size, String search) {
        if (search != null && !search.trim().isEmpty()) {
            String q = "%" + search.trim().toLowerCase() + "%";
            return batchRepository.searchBatches(q, PageRequest.of(page, size, Sort.by("receivedAt").descending()));
        }
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

    @Transactional(rollbackFor = Exception.class)
    public void markBatchDamage(UUID batchId, int quantity, String damageType, String reason, String username) {
        if (quantity <= 0) {
            throw new RuntimeException("Quantity must be greater than 0");
        }

        StockBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found: " + batchId));

        int available = batch.getSecondaryRemaining() != null ? batch.getSecondaryRemaining() : 0;
        if (available < quantity) {
            throw new RuntimeException("Insufficient stock in batch " + batch.getBatchNumber()
                    + " | Available: " + available + " | Requested: " + quantity);
        }

        Product product = batch.getProduct();
        
        // Compute value loss
        BigDecimal buyPricePerSecondary = product.getBuyPricePerSecondary();
        if (buyPricePerSecondary == null || buyPricePerSecondary.compareTo(BigDecimal.ZERO) == 0) {
            buyPricePerSecondary = batch.getBuyPricePerSecondary(product.getSecondaryPerPrimary());
        }
        BigDecimal valueLoss = buyPricePerSecondary.multiply(BigDecimal.valueOf(quantity));

        // Resolve user
        com.shop.modules.user.User user = null;
        if (username != null && !username.equals("System")) {
            user = userRepository.findByPhone(username).orElse(null);
        }

        // Map damageType to ClaimStatus
        ClaimStatus claimStatus = ClaimStatus.NON_CLAIMABLE;
        if ("PERMANENT".equalsIgnoreCase(damageType)) {
            claimStatus = ClaimStatus.PERMANENT_LOSS;
        } else if ("RECLAIMABLE".equalsIgnoreCase(damageType)) {
            claimStatus = ClaimStatus.CLAIMABLE;
        }

        // Map reason string to DamageReason enum
        DamageReason damageReason = DamageReason.OTHER;
        String notes = reason;
        try {
            damageReason = DamageReason.valueOf(reason.toUpperCase().trim());
            notes = "Marked as damage from batch action.";
        } catch (IllegalArgumentException e) {
            // Keep DamageReason.OTHER and use the original reason as notes
        }

        // Create DamageLog
        com.shop.modules.product.UnitType unitType = com.shop.modules.product.UnitType.SINGLE;
        if (product.getSecondaryUnit() != null) {
            try {
                unitType = com.shop.modules.product.UnitType.valueOf(product.getSecondaryUnit().toUpperCase().trim());
            } catch (Exception ignored) {}
        }

        DamageLog damage = DamageLog.builder()
                .product(product)
                .batch(batch)
                .unitType(unitType)
                .unitLevel(UnitLevel.SECONDARY)
                .claimStatus(claimStatus)
                .quantity(quantity)
                .reason(damageReason)
                .valueLoss(valueLoss)
                .notes(notes)
                .loggedBy(user)
                .build();
        damageLogRepository.save(damage);

        // Deduct from batch
        int qtyBefore = batch.getSecondaryRemaining();
        int qtyAfter = qtyBefore - quantity;
        batch.setSecondaryRemaining(qtyAfter);
        batch.setExhausted(qtyAfter == 0);
        batchRepository.save(batch);

        // Deduct from total stock
        Stock stock = inventoryService.getOrCreateStock(product.getId());
        int totalQtyBefore = stock.getTotalSecondaryUnits();
        int totalQtyAfter = Math.max(0, totalQtyBefore - quantity);
        stock.setTotalSecondaryUnits(totalQtyAfter);
        inventoryService.normalizeStock(stock, product);
        stockRepository.save(stock);

        // Log movement as DAMAGE
        movementService.logMovement(
                product,
                batch,
                "DAMAGE",
                -quantity,
                totalQtyBefore,
                totalQtyAfter,
                buyPricePerSecondary,
                username,
                batch.getInvoiceNumber(),
                reason
        );
    }
}