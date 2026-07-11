package com.shop.modules.stock;

import com.shop.modules.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockMovementService {

    private final StockMovementRepository movementRepository;

    @Transactional
    public void logMovement(
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

        LocalDate supplierInvoiceDate = null;
        String supplierName = null;
        if (batch != null) {
            supplierInvoiceDate = batch.getSupplierInvoiceDate();
            supplierName = batch.getSupplierName();
        }

        logMovement(product, batch, movementType, quantity, quantityBefore, quantityAfter,
                unitPrice, username, referenceNumber, remarks, supplierInvoiceDate, supplierName);
    }

    @Transactional
    public void logMovement(
            Product product,
            StockBatch batch,
            String movementType,
            int quantity,
            Integer quantityBefore,
            Integer quantityAfter,
            BigDecimal unitPrice,
            String username,
            String referenceNumber,
            String remarks,
            LocalDate supplierInvoiceDate,
            String supplierName) {

        BigDecimal qtyBD = BigDecimal.valueOf(Math.abs(quantity));
        BigDecimal price = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        BigDecimal totalValue = qtyBD.multiply(price);

        BigDecimal buyPriceWithoutTax = null;
        BigDecimal buyPriceWithTax = null;
        BigDecimal gstPercent = null;
        Integer secondaryPerPrimary = null;
        String receiveSource = null;

        if (batch != null) {
            buyPriceWithoutTax = batch.getBuyPriceWithoutTax();
            buyPriceWithTax = batch.getBuyPriceWithTax();
            gstPercent = batch.getGstPercent();
            receiveSource = batch.getReceiveSource();
        }
        if (product != null) {
            secondaryPerPrimary = product.getSecondaryPerPrimary();
            if (buyPriceWithoutTax == null) {
                buyPriceWithoutTax = product.getBuyPriceWithoutTax();
            }
            if (buyPriceWithTax == null) {
                buyPriceWithTax = product.getBuyPriceWithTax();
            }
            if (gstPercent == null) {
                gstPercent = product.getGstPercent();
            }
        }

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
                .supplierInvoiceDate(supplierInvoiceDate)
                .supplierName(supplierName)
                .buyPriceWithoutTax(buyPriceWithoutTax)
                .buyPriceWithTax(buyPriceWithTax)
                .gstPercent(gstPercent)
                .secondaryPerPrimary(secondaryPerPrimary)
                .receiveSource(receiveSource)
                .build();

        movementRepository.save(movement);
    }

    public Page<StockMovement> getFilteredMovements(
            UUID productId, String movementType, LocalDateTime start, LocalDateTime end, String search, Pageable pageable) {
        return movementRepository.findFilteredMovements(productId, movementType, start, end, search, pageable);
    }

    public List<StockMovement> getAllFilteredMovements(
            UUID productId, String movementType, LocalDateTime start, LocalDateTime end, String search) {
        return movementRepository.findAllFilteredMovements(productId, movementType, start, end, search);
    }
}
