package com.shop.modules.stock;

import com.shop.modules.product.Product;
import com.shop.modules.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockRestorationService {

    private final StockRepository stockRepository;
    private final StockBatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final StockInventoryService inventoryService;
    private final StockMovementService movementService;

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

    @Transactional
    public void addBackStock(UUID productId, int primaryQty, int secondaryQty) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        addBackStock(productId, primaryQty, secondaryQty, "System", null, product.getBuyPricePerSecondary(), "Restored general inventory stock");
    }

    @Transactional
    public void addBackStock(UUID productId, int primaryQty, int secondaryQty, String username, String referenceNumber, BigDecimal unitCostPrice, String remarks) {
        addBackStock(productId, primaryQty, secondaryQty, username, referenceNumber, unitCostPrice, remarks, "RETURN_IN");
    }

    @Transactional
    public void addBackStock(UUID productId, int primaryQty, int secondaryQty, String username, String referenceNumber, BigDecimal unitCostPrice, String remarks, String movementType) {
        Stock stock = inventoryService.getOrCreateStock(productId);
        int qtyBefore = stock.getTotalSecondaryUnits();
        int qtyAfter = qtyBefore + secondaryQty;

        stock.setTotalSecondaryUnits(qtyAfter);
        inventoryService.normalizeStock(stock, stock.getProduct());
        stockRepository.save(stock);

        BigDecimal price = unitCostPrice != null ? unitCostPrice : stock.getProduct().getBuyPricePerSecondary();
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
    public void addBackStockToBatch(UUID productId, UUID batchId, int primaryQty, int secondaryQty, String username, String referenceNumber, BigDecimal unitCostPrice, String remarks) {
        addBackStockToBatch(productId, batchId, primaryQty, secondaryQty, username, referenceNumber, unitCostPrice, remarks, "RETURN_IN");
    }

    @Transactional
    public void addBackStockToBatch(UUID productId, UUID batchId, int primaryQty, int secondaryQty, String username, String referenceNumber, BigDecimal unitCostPrice, String remarks, String movementType) {
        Stock stock = inventoryService.getOrCreateStock(productId);
        int qtyBefore = stock.getTotalSecondaryUnits();
        int qtyAfter = qtyBefore + secondaryQty;

        stock.setTotalSecondaryUnits(qtyAfter);
        inventoryService.normalizeStock(stock, stock.getProduct());
        stockRepository.save(stock);

        BigDecimal price = unitCostPrice != null ? unitCostPrice : stock.getProduct().getBuyPricePerSecondary();

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
    public void addBackStockToSpecificBatch(UUID batchId, int secondaryQty, String username, String referenceNumber, BigDecimal unitCostPrice, String remarks) {
        addBackStockToSpecificBatch(batchId, secondaryQty, username, referenceNumber, unitCostPrice, remarks, "RETURN_IN");
    }

    @Transactional
    public void addBackStockToSpecificBatch(UUID batchId, int secondaryQty, String username, String referenceNumber, BigDecimal unitCostPrice, String remarks, String movementType) {
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
                unitCostPrice != null ? unitCostPrice : batch.getBuyPricePerSecondary(batch.getProduct().getSecondaryPerPrimary()),
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
}
