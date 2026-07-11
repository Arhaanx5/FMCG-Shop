package com.shop.modules.billing;

import com.shop.modules.billing.dto.CreateBillRequest;
import com.shop.modules.product.Product;
import com.shop.modules.stock.Stock;
import com.shop.modules.stock.StockBatch;
import com.shop.modules.stock.StockService;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BillCalculationHelper {

    public int getSafeSecondaryPerPrimary(Product product) {
        return (product.getSecondaryPerPrimary() != null && product.getSecondaryPerPrimary() > 0)
                ? product.getSecondaryPerPrimary()
                : 1;
    }

    public BigDecimal getRateForUnit(Product product, String unitType) {
        boolean isPrimary = unitType.equalsIgnoreCase(product.getPrimaryUnit());
        if (isPrimary) {
            if (product.getSellPricePrimary() == null || product.getSellPricePrimary().compareTo(BigDecimal.ZERO) == 0) {
                throw new RuntimeException("Sell price not set for " + product.getPrimaryUnit() + " of: " + product.getName());
            }
            return product.getSellPricePrimary();
        } else {
            if (product.getSellPriceSecondary() == null || product.getSellPriceSecondary().compareTo(BigDecimal.ZERO) == 0) {
                throw new RuntimeException("Sell price not set for " + product.getSecondaryUnit() + " of: " + product.getName());
            }
            return product.getSellPriceSecondary();
        }
    }

    public String generateBillNumber(BillRepository billRepository) {
        Long next = billRepository.getNextBillSequence();
        return String.format("BILL-%05d", next);
    }

    public void checkStockAvailability(Product product, CreateBillRequest.BillItemRequest itemReq, boolean isDraft, StockService stockService) {
        Stock stock = stockService.getOrCreateStock(product.getId());
        String unitType = itemReq.getUnitType().name();
        boolean isPrimary = unitType.equalsIgnoreCase(product.getPrimaryUnit());
        boolean isSecondary = unitType.equalsIgnoreCase(product.getSecondaryUnit());

        if (!isPrimary && !isSecondary) {
            throw new RuntimeException("Invalid unit '" + unitType + "' for: " + product.getName()
                    + " | Valid: " + product.getPrimaryUnit() + " or " + product.getSecondaryUnit());
        }

        int totalQtyRequested = itemReq.getQuantity() + itemReq.getFreeQuantity();
        int totalSecondaryRequested = isPrimary
                ? totalQtyRequested * getSafeSecondaryPerPrimary(product)
                : totalQtyRequested;

        if (itemReq.isOffer()) {
            if (itemReq.getBatchId() != null) {
                StockBatch batch = stockService.getBatchById(itemReq.getBatchId());
                int available = batch.getOfferSecondaryRemaining() != null ? batch.getOfferSecondaryRemaining() : 0;
                if (available < totalSecondaryRequested) {
                    throw new RuntimeException("Insufficient offer stock in batch " + batch.getBatchNumber()
                            + " for: " + product.getName()
                            + " | Available: " + available
                            + " | Requested: " + totalSecondaryRequested);
                }
            } else {
                List<StockBatch> activeBatches = stockService.getBatchesByProduct(product.getId());
                int totalAvailable = 0;
                for (StockBatch b : activeBatches) {
                    int avail = b.getOfferSecondaryRemaining() != null ? b.getOfferSecondaryRemaining() : 0;
                    if (avail > 0) {
                        totalAvailable += avail;
                    }
                }
                if (totalAvailable < totalSecondaryRequested) {
                    throw new RuntimeException("Insufficient offer stock for: " + product.getName()
                            + " | Available: " + totalAvailable
                            + " | Requested: " + totalSecondaryRequested);
                }
            }
            return;
        }

        if (itemReq.getBatchId() != null) {
            StockBatch batch = stockService.getBatchById(itemReq.getBatchId());
            int reserved = batch.getSecondarySoftReserved() != null ? batch.getSecondarySoftReserved() : 0;
            int available = isDraft ? (batch.getSecondaryRemaining() - reserved) : batch.getSecondaryRemaining();
            if (available < totalSecondaryRequested) {
                throw new RuntimeException("Insufficient " + (isDraft ? "virtual " : "") + "stock in batch " + batch.getBatchNumber()
                        + " for: " + product.getName()
                        + " | Available: " + available
                        + " | Requested: " + totalSecondaryRequested);
            }
        } else {
            List<StockBatch> activeBatches = stockService.getBatchesByProduct(product.getId());
            if (activeBatches.isEmpty()) {
                throw new RuntimeException("No active stock batch found for: " + product.getName());
            }
            int totalAvailable = 0;
            for (StockBatch b : activeBatches) {
                int reserved = b.getSecondarySoftReserved() != null ? b.getSecondarySoftReserved() : 0;
                int avail = isDraft ? (b.getSecondaryRemaining() - reserved) : b.getSecondaryRemaining();
                if (avail > 0) {
                    totalAvailable += avail;
                }
            }
            if (totalAvailable < totalSecondaryRequested) {
                throw new RuntimeException("Insufficient " + (isDraft ? "virtual " : "") + "stock for: " + product.getName()
                        + " | Available: " + totalAvailable
                        + " | Requested: " + totalSecondaryRequested);
            }
        }
    }

    public void checkPriceOverrideLimits(Product product, CreateBillRequest.BillItemRequest itemReq, User user, boolean overrideCost) {
        if (itemReq.isOffer()) {
            return;
        }
        if (itemReq.getCustomRate() == null || itemReq.getCustomRate().compareTo(BigDecimal.ZERO) < 0) {
            return;
        }
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }

        boolean isPrimary = itemReq.getUnitType().name().equalsIgnoreCase(product.getPrimaryUnit());
        BigDecimal costLimit = isPrimary ? product.getBuyPriceWithTax()
                : product.getBuyPriceWithTax().divide(BigDecimal.valueOf(getSafeSecondaryPerPrimary(product)), 4, RoundingMode.HALF_UP);

        if (itemReq.getCustomRate().compareTo(costLimit) < 0) {
            if (user.getRole() == UserRole.SALESMAN || user.getRole() == UserRole.DELIVERY_BOY) {
                throw new RuntimeException("Price override not allowed: Custom rate for "
                        + product.getName() + " cannot be lower than purchase cost of ₹" + costLimit);
            } else if (user.getRole() == UserRole.MANAGER) {
                if (!overrideCost) {
                    throw new com.shop.common.BelowCostWarningException("Warning: Custom rate for "
                            + product.getName() + " (₹" + itemReq.getCustomRate() + ") is lower than purchase cost of ₹" + costLimit);
                }
            }
        }
    }
}
