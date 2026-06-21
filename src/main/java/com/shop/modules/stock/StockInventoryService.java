package com.shop.modules.stock;

import com.shop.modules.product.Product;
import com.shop.modules.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import com.shop.modules.user.UserRepository;

@Service
@RequiredArgsConstructor
public class StockInventoryService {

    private final StockRepository stockRepository;
    private final StockBatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final StockAdjustmentLogRepository stockAdjustmentLogRepository;
    private final UserRepository userRepository;
    private final StockMovementService movementService;

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
                    .lastUpdated(LocalDateTime.now())
                    .build();
        }

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

    public void normalizeStock(Stock stock, Product product) {
        if (product == null || product.getSecondaryPerPrimary() == null || product.getSecondaryPerPrimary() <= 0) {
            return;
        }
        int secondaryPerPrimary = product.getSecondaryPerPrimary();
        stock.setTotalPrimaryUnits(stock.getTotalSecondaryUnits() / secondaryPerPrimary);
        stock.setOpenPrimaryRemaining(stock.getTotalSecondaryUnits() % secondaryPerPrimary);
        stock.setHasOpenPrimary(stock.getOpenPrimaryRemaining() > 0);
    }

    @Transactional(rollbackFor = Exception.class)
    public void adjustStock(UUID batchId, int newSecondaryRemaining, Integer newOfferSecondaryRemaining, BigDecimal newBuyPriceWithoutTax, String reason, String adjustedBy) {
        StockBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found: " + batchId));

        Product product = batch.getProduct();
        Stock stock = getOrCreateStock(product.getId());

        int oldSecondaryRemaining = batch.getSecondaryRemaining();
        int change = newSecondaryRemaining - oldSecondaryRemaining;

        int oldOfferRemaining = batch.getOfferSecondaryRemaining() != null ? batch.getOfferSecondaryRemaining() : 0;
        int offerChange = newOfferSecondaryRemaining != null ? (newOfferSecondaryRemaining - oldOfferRemaining) : 0;

        int totalChange = change + offerChange;

        int qtyBefore = stock.getTotalSecondaryUnits();
        int qtyAfter = qtyBefore + totalChange;

        batch.setSecondaryRemaining(newSecondaryRemaining);
        batch.setExhausted(newSecondaryRemaining == 0);

        // Also sync secondaryReceived so that "Received" display, sold-qty calculation,
        // and getBuyPricePerSecondary dilution formula stay consistent after adjustment.
        if (change != 0) {
            int oldReceived = batch.getSecondaryReceived() != null ? batch.getSecondaryReceived() : 0;
            batch.setSecondaryReceived(Math.max(0, oldReceived + change));
        }
        if (newOfferSecondaryRemaining != null) {
            batch.setOfferSecondaryRemaining(newOfferSecondaryRemaining);
            if (offerChange != 0) {
                int oldOfferReceived = batch.getOfferSecondaryReceived() != null ? batch.getOfferSecondaryReceived() : 0;
                batch.setOfferSecondaryReceived(Math.max(0, oldOfferReceived + offerChange));
            }
        }

        BigDecimal oldBuyPrice = batch.getBuyPriceWithoutTax();
        if (newBuyPriceWithoutTax != null) {
            batch.setBuyPriceWithoutTax(newBuyPriceWithoutTax);
            BigDecimal gstRate = product.getGstPercent().divide(BigDecimal.valueOf(100));
            BigDecimal taxAmount = newBuyPriceWithoutTax.multiply(gstRate).setScale(2, java.math.RoundingMode.HALF_UP);
            batch.setBuyPriceWithTax(newBuyPriceWithoutTax.add(taxAmount));
        }

        if (newSecondaryRemaining > 0) {
            batch.setExhausted(false);
            if (batch.getBatchStatus() == BatchStatus.WRITTEN_OFF || batch.getBatchStatus() == BatchStatus.EXPIRED) {
                batch.setBatchStatus(BatchStatus.ACTIVE);
            }
        }

        batchRepository.save(batch);

        stock.setTotalSecondaryUnits(qtyAfter);
        normalizeStock(stock, product);
        stockRepository.save(stock);

        String adjustedByName = adjustedBy;
        if (adjustedBy != null && !adjustedBy.equals("System")) {
            adjustedByName = userRepository.findByPhone(adjustedBy)
                    .map(com.shop.modules.user.User::getName)
                    .orElse(adjustedBy);
        }

        StockAdjustmentLog log = StockAdjustmentLog.builder()
                .batchId(batchId)
                .batchNumber(batch.getBatchNumber())
                .productName(product.getName())
                .oldSecondaryRemaining(oldSecondaryRemaining)
                .newSecondaryRemaining(newSecondaryRemaining)
                .adjustedBy(adjustedByName)
                .reason(reason)
                .timestamp(LocalDateTime.now())
                .build();
        stockAdjustmentLogRepository.save(log);

        movementService.logMovementAsync(
                product,
                batch,
                "ADJUSTMENT",
                totalChange,
                qtyBefore,
                qtyAfter,
                batch.getBuyPricePerSecondary(product.getSecondaryPerPrimary()),
                adjustedByName,
                null,
                reason
        );
    }
}
