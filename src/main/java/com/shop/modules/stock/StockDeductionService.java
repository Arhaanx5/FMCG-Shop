package com.shop.modules.stock;

import com.shop.modules.product.Product;
import com.shop.modules.product.ProductRepository;
import com.shop.modules.stock.dto.BatchDeductionRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockDeductionService {

    private final StockRepository stockRepository;
    private final StockBatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final StockInventoryService inventoryService;
    private final StockMovementService movementService;

    @Transactional
    public void deductOfferUnits(UUID batchId, int quantity) {
        deductOfferUnits(batchId, quantity, "System", null, BigDecimal.ZERO, "Deducted offer units");
    }

    @Transactional
    public void deductOfferUnits(UUID batchId, int quantity, String username, String referenceNumber, BigDecimal unitPrice, String remarks) {
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
    public List<BatchDeductionRecord> deductByPrimary(UUID productId, int quantity, UUID batchId, String username, String referenceNumber, BigDecimal unitPrice, String remarks) {
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

        List<BatchDeductionRecord> deductions = deductFromBatches(productId, secondaryToDeduct, batchId);

        BigDecimal price = unitPrice != null ? unitPrice : product.getBuyPricePerSecondary();
        movementService.logMovement(
                product,
                batchId != null ? batchRepository.findById(batchId).orElse(null) : null,
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
    public List<BatchDeductionRecord> deductBySecondary(UUID productId, int quantity) {
        return deductBySecondary(productId, quantity, null);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public List<BatchDeductionRecord> deductBySecondary(UUID productId, int quantity, UUID batchId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        return deductBySecondary(productId, quantity, batchId, "System", null, product.getBuyPricePerSecondary(), "Deducted secondary units");
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public List<BatchDeductionRecord> deductBySecondary(UUID productId, int quantity, UUID batchId, String username, String referenceNumber, BigDecimal unitPrice, String remarks) {
        return deductBySecondary(productId, quantity, batchId, username, referenceNumber, unitPrice, remarks, "SALE");
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public List<BatchDeductionRecord> deductBySecondary(UUID productId, int quantity, UUID batchId, String username, String referenceNumber, BigDecimal unitPrice, String remarks, String movementType) {
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

        List<BatchDeductionRecord> deductions = deductFromBatches(productId, quantity, batchId);

        BigDecimal price = unitPrice != null ? unitPrice : product.getBuyPricePerSecondary();
        movementService.logMovement(
                product,
                batchId != null ? batchRepository.findById(batchId).orElse(null) : null,
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

    private List<BatchDeductionRecord> deductFromBatches(UUID productId, int secondaryQty, UUID batchId) {
        List<BatchDeductionRecord> deductions = new ArrayList<>();
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
            deductions.add(new BatchDeductionRecord(batchId, secondaryQty));
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
                deductions.add(new BatchDeductionRecord(lockedBatch.getId(), deduct));
                remaining -= deduct;
            }
        }
        if (remaining > 0) {
            throw new RuntimeException("Insufficient stock to complete deduction: " + remaining + " units missing");
        }
        return deductions;
    }
}
