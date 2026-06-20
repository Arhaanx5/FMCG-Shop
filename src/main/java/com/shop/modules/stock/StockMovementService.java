package com.shop.modules.stock;

import com.shop.modules.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class StockMovementService {

    private final StockMovementRepository movementRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> logMovementAsync(
            Product product,
            StockBatch batch,
            String movementType,
            int quantity,
            Integer quantityBefore,
            Integer quantityAfter,
            BigDecimal unitPrice,
            String username,
            String referenceNumber,
            String remarks) {

        BigDecimal qtyBD = BigDecimal.valueOf(Math.abs(quantity));
        BigDecimal price = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        BigDecimal totalValue = qtyBD.multiply(price);

        StockMovement movement = StockMovement.builder()
                .timestamp(LocalDateTime.now())
                .product(product)
                .batch(batch)
                .movementType(movementType)
                .quantity(quantity)
                .quantityBefore(quantityBefore)
                .quantityAfter(quantityAfter)
                .unitPrice(price)
                .totalValue(totalValue)
                .username(username != null ? username : "System")
                .referenceNumber(referenceNumber)
                .remarks(remarks)
                .build();

        movementRepository.save(movement);
        return CompletableFuture.completedFuture(null);
    }

    public Page<StockMovement> getFilteredMovements(
            UUID productId, String movementType, LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return movementRepository.findFilteredMovements(productId, movementType, start, end, pageable);
    }
}
