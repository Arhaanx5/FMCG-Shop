package com.shop.modules.product;

import com.shop.modules.product.dto.ProductResponse;
import com.shop.modules.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductMapper {

    private final StockRepository stockRepository;

    public ProductResponse toResponse(Product product) {
        var stock = stockRepository.findByProductId(product.getId()).orElse(null);

        int totalPrimary = stock != null ? stock.getTotalPrimaryUnits() : 0;
        int totalSecondary = stock != null ? stock.getTotalSecondaryUnits() : 0;
        int openRemaining = stock != null ? stock.getOpenPrimaryRemaining() : 0;
        boolean hasOpen = stock != null && stock.getHasOpenPrimary() != null && stock.getHasOpenPrimary();

        boolean isLowStock = totalSecondary <= product.getLowStockAlert() || totalSecondary <= 0;

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .productCode(product.getProductCode())
                .brand(product.getBrand())
                .category(product.getCategory())
                .otherCategoryDetail(product.getOtherCategoryDetail())
                .gstPercent(product.getGstPercent())
                .cessPercent(product.getCessPercent())
                .primaryUnit(product.getPrimaryUnit())
                .secondaryUnit(product.getSecondaryUnit())
                .secondaryPerPrimary(product.getSecondaryPerPrimary())
                .canSellPrimary(product.getCanSellPrimary())
                .canSellSecondary(product.getCanSellSecondary())
                .buyPriceWithoutTax(product.getBuyPriceWithoutTax())
                .buyPriceWithTax(product.getBuyPriceWithTax())
                .sellPricePrimary(product.getSellPricePrimary())
                .sellPriceSecondary(product.getSellPriceSecondary())
                .totalPrimaryUnits(totalPrimary)
                .totalSecondaryUnits(totalSecondary)
                .openPrimaryRemaining(openRemaining)
                .hasOpenPrimary(hasOpen)
                .lowStockAlert(product.getLowStockAlert())
                .lowStockUnit(product.getLowStockUnit())
                .isLowStock(isLowStock)
                .active(product.getActive())
                .hsnCode(product.getHsnCode())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
