package com.shop.modules.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockAdjustmentService {

    private final StockBatchRepository batchRepository;
    private final StockService stockService;

    @Transactional(rollbackFor = Exception.class)
    public void adjustPhysicalStock(UUID batchId, int physicalCount, String reason, String username) {
        StockBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found: " + batchId));

        int currentStock = batch.getSecondaryRemaining() != null ? batch.getSecondaryRemaining() : 0;
        int difference = physicalCount - currentStock;

        if (difference == 0) {
            throw new RuntimeException("No change detected");
        }

        // Call stockService.adjustStock with physicalCount as newSecondaryRemaining,
        // and keep newOfferSecondaryRemaining and newBuyPriceWithoutTax unchanged.
        stockService.adjustStock(
                batchId,
                physicalCount,
                batch.getOfferSecondaryRemaining(),
                batch.getBuyPriceWithoutTax(),
                reason,
                username
        );
    }
}
