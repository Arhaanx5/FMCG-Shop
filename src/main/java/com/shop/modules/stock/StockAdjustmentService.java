package com.shop.modules.stock;

import com.shop.modules.product.Product;
import com.shop.modules.damage.ClaimStatus;
import com.shop.modules.damage.DamageLog;
import com.shop.modules.damage.DamageLogRepository;
import com.shop.modules.damage.DamageReason;
import com.shop.modules.damage.UnitLevel;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockAdjustmentService {

    private final StockBatchRepository batchRepository;
    private final StockRepository stockRepository;
    private final StockInventoryService inventoryService;
    private final StockMovementService movementService;
    private final DamageLogRepository damageLogRepository;
    private final UserRepository userRepository;
    private final StockAdjustmentLogRepository stockAdjustmentLogRepository;

    @Transactional(rollbackFor = Exception.class)
    public void adjustPhysicalStock(UUID batchId, int physicalCount, String reason, String username) {
        StockBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found: " + batchId));

        int currentStock = batch.getSecondaryRemaining() != null ? batch.getSecondaryRemaining() : 0;
        int difference = physicalCount - currentStock;

        if (difference == 0) {
            throw new RuntimeException("No change detected");
        }

        inventoryService.adjustStock(
                batchId,
                physicalCount,
                batch.getOfferSecondaryRemaining(),
                batch.getBuyPriceWithoutTax(),
                reason,
                username
        );
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

        User user = null;
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
}
