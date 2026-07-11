package com.shop.modules.stock;

import com.shop.modules.product.Product;
import com.shop.modules.stock.dto.BatchDeductionRecord;
import com.shop.modules.damage.DamageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service orchestrator for Stock.
 * Delegating state mutations to specialized sub-services.
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final StockBatchRepository batchRepository;
    private final StockAdjustmentLogRepository stockAdjustmentLogRepository;

    // Decomposed Services Injection
    private final StockReceiveService receiveService;
    private final StockMovementService movementService;
    private final StockInventoryService inventoryService;
    private final StockDeductionService stockDeductionService;
    private final StockRestorationService stockRestorationService;
    private final StockAdjustmentService stockAdjustmentService;
    private final DamageService damageService;

    // ─────────────────────────────────────────────────────────────
    // Read (Query) Operations
    // ─────────────────────────────────────────────────────────────

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
            return batchRepository.searchBatches(q, PageRequest.of(page, size, Sort.by(Sort.Order.desc("stockReceivedDate"), Sort.Order.desc("receivedAt"))));
        }
        return batchRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Order.desc("stockReceivedDate"), Sort.Order.desc("receivedAt"))));
    }

    public List<StockBatch> getBatchesByInvoice(String invoiceNumber) {
        if (invoiceNumber == null || invoiceNumber.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return batchRepository.findByInvoiceNumberIgnoreCase(invoiceNumber.trim());
    }

    public List<StockAdjustmentLog> getAdjustmentLogs() {
        return stockAdjustmentLogRepository.findAllByOrderByTimestampDesc();
    }

    public Page<StockAdjustmentLog> getAdjustmentLogsPaged(int page, int size) {
        return stockAdjustmentLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(page, size));
    }

    // ─────────────────────────────────────────────────────────────
    // Write (Command) Operations — Delegated
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public StockBatch receiveStock(ReceiveStockRequest req, String addedByUsername) {
        return receiveService.receiveStock(req, addedByUsername);
    }

    @Transactional
    public void deductOfferUnits(UUID batchId, int quantity) {
        stockDeductionService.deductOfferUnits(batchId, quantity);
    }

    @Transactional
    public void deductOfferUnits(UUID batchId, int quantity, String username, String referenceNumber, BigDecimal unitPrice, String remarks) {
        stockDeductionService.deductOfferUnits(batchId, quantity, username, referenceNumber, unitPrice, remarks);
    }

    @Transactional
    public void addBackOfferStock(UUID productId, UUID batchId, int quantity) {
        stockRestorationService.addBackOfferStock(productId, batchId, quantity);
    }

    @Transactional
    public void addBackOfferStock(UUID productId, UUID batchId, int quantity, String username, String referenceNumber, BigDecimal unitPrice, String remarks) {
        stockRestorationService.addBackOfferStock(productId, batchId, quantity, username, referenceNumber, unitPrice, remarks);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public void deductByPrimary(UUID productId, int quantity) {
        stockDeductionService.deductByPrimary(productId, quantity);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public void deductByPrimary(UUID productId, int quantity, UUID batchId) {
        stockDeductionService.deductByPrimary(productId, quantity, batchId);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public List<BatchDeductionRecord> deductByPrimary(UUID productId, int quantity, UUID batchId, String username, String referenceNumber, BigDecimal unitPrice, String remarks) {
        return stockDeductionService.deductByPrimary(productId, quantity, batchId, username, referenceNumber, unitPrice, remarks);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public List<BatchDeductionRecord> deductBySecondary(UUID productId, int quantity) {
        return stockDeductionService.deductBySecondary(productId, quantity);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public List<BatchDeductionRecord> deductBySecondary(UUID productId, int quantity, UUID batchId) {
        return stockDeductionService.deductBySecondary(productId, quantity, batchId);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public List<BatchDeductionRecord> deductBySecondary(UUID productId, int quantity, UUID batchId, String username, String referenceNumber, BigDecimal unitPrice, String remarks) {
        return stockDeductionService.deductBySecondary(productId, quantity, batchId, username, referenceNumber, unitPrice, remarks);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public List<BatchDeductionRecord> deductBySecondary(UUID productId, int quantity, UUID batchId, String username, String referenceNumber, BigDecimal unitPrice, String remarks, String movementType) {
        return stockDeductionService.deductBySecondary(productId, quantity, batchId, username, referenceNumber, unitPrice, remarks, movementType);
    }

    @Transactional
    public void addBackStock(UUID productId, int primaryQty, int secondaryQty) {
        stockRestorationService.addBackStock(productId, primaryQty, secondaryQty);
    }

    @Transactional
    public void addBackStock(UUID productId, int primaryQty, int secondaryQty, String username, String referenceNumber, BigDecimal unitCostPrice, String remarks) {
        stockRestorationService.addBackStock(productId, primaryQty, secondaryQty, username, referenceNumber, unitCostPrice, remarks);
    }

    @Transactional
    public void addBackStock(UUID productId, int primaryQty, int secondaryQty, String username, String referenceNumber, BigDecimal unitCostPrice, String remarks, String movementType) {
        stockRestorationService.addBackStock(productId, primaryQty, secondaryQty, username, referenceNumber, unitCostPrice, remarks, movementType);
    }

    @Transactional
    public void addBackStockToBatch(UUID productId, UUID batchId, int primaryQty, int secondaryQty, String username, String referenceNumber, BigDecimal unitCostPrice, String remarks) {
        stockRestorationService.addBackStockToBatch(productId, batchId, primaryQty, secondaryQty, username, referenceNumber, unitCostPrice, remarks);
    }

    @Transactional
    public void addBackStockToBatch(UUID productId, UUID batchId, int primaryQty, int secondaryQty, String username, String referenceNumber, BigDecimal unitCostPrice, String remarks, String movementType) {
        stockRestorationService.addBackStockToBatch(productId, batchId, primaryQty, secondaryQty, username, referenceNumber, unitCostPrice, remarks, movementType);
    }

    @Transactional
    public void addBackStockToSpecificBatch(UUID batchId, int secondaryQty) {
        stockRestorationService.addBackStockToSpecificBatch(batchId, secondaryQty);
    }

    @Transactional
    public void addBackStockToSpecificBatch(UUID batchId, int secondaryQty, String username, String referenceNumber, BigDecimal unitCostPrice, String remarks) {
        stockRestorationService.addBackStockToSpecificBatch(batchId, secondaryQty, username, referenceNumber, unitCostPrice, remarks);
    }

    @Transactional
    public void addBackStockToSpecificBatch(UUID batchId, int secondaryQty, String username, String referenceNumber, BigDecimal unitCostPrice, String remarks, String movementType) {
        stockRestorationService.addBackStockToSpecificBatch(batchId, secondaryQty, username, referenceNumber, unitCostPrice, remarks, movementType);
    }

    @Transactional
    public void restoreStockToBatches(UUID productId, int secondaryQty) {
        stockRestorationService.restoreStockToBatches(productId, secondaryQty);
    }

    @Transactional
    public void restoreStockToBatches(UUID productId, int secondaryQty, String username, String referenceNumber, BigDecimal unitPrice, String remarks) {
        stockRestorationService.restoreStockToBatches(productId, secondaryQty, username, referenceNumber, unitPrice, remarks);
    }

    @Transactional
    public void restoreStockToBatches(UUID productId, int secondaryQty, String username, String referenceNumber, BigDecimal unitPrice, String remarks, String movementType) {
        stockRestorationService.restoreStockToBatches(productId, secondaryQty, username, referenceNumber, unitPrice, remarks, movementType);
    }

    @Transactional(rollbackFor = Exception.class)
    public void adjustStock(UUID batchId, int newSecondaryRemaining, Integer newOfferSecondaryRemaining, BigDecimal newBuyPriceWithoutTax, String reason, String adjustedBy) {
        stockAdjustmentService.adjustStock(batchId, newSecondaryRemaining, newOfferSecondaryRemaining, newBuyPriceWithoutTax, reason, adjustedBy);
    }

    @Transactional(rollbackFor = Exception.class)
    public void writeOffExpiredBatch(UUID batchId, String adjustedBy) {
        stockAdjustmentService.writeOffExpiredBatch(batchId, adjustedBy);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markBatchDamage(UUID batchId, int quantity, String damageType, String reason, String username) {
        damageService.markBatchDamage(batchId, quantity, damageType, reason, username);
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
        private String receiveSource;
        private boolean logAsExpense;
    }
}