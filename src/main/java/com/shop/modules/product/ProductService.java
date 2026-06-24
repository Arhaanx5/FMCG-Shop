package com.shop.modules.product;

import com.shop.modules.product.dto.CreateProductRequest;
import com.shop.modules.product.dto.ProductResponse;
import com.shop.modules.stock.StockRepository;
import com.shop.modules.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final StockService stockService;

    private ProductResponse toResponse(Product product) {

        var stock = stockService.getOrCreateStock(product.getId());

        int totalPrimary = stock != null
                ? stock.getTotalPrimaryUnits() : 0;
        int totalSecondary = stock != null
                ? stock.getTotalSecondaryUnits() : 0;
        int openRemaining = stock != null
                ? stock.getOpenPrimaryRemaining() : 0;
        boolean hasOpen = stock != null
                && stock.getHasOpenPrimary() != null
                && stock.getHasOpenPrimary();

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
                .secondaryPerPrimary(
                        product.getSecondaryPerPrimary())
                .canSellPrimary(product.getCanSellPrimary())
                .canSellSecondary(
                        product.getCanSellSecondary())
                .buyPriceWithoutTax(
                        product.getBuyPriceWithoutTax())
                .buyPriceWithTax(
                        product.getBuyPriceWithTax())
                .sellPricePrimary(
                        product.getSellPricePrimary())
                .sellPriceSecondary(
                        product.getSellPriceSecondary())
                .totalPrimaryUnits(totalPrimary)
                .totalSecondaryUnits(totalSecondary)
                .openPrimaryRemaining(openRemaining)
                .hasOpenPrimary(hasOpen)
                .lowStockAlert(product.getLowStockAlert())
                .lowStockUnit(product.getLowStockUnit())
                .isLowStock(isLowStock)
                .active(product.getActive())
                .createdAt(product.getCreatedAt())
                .build();
    }

    public Product findProductByIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new RuntimeException("Identifier cannot be blank");
        }
        String trimmed = identifier.trim();
        try {
            UUID uuid = UUID.fromString(trimmed);
            return productRepository.findById(uuid)
                    .orElseThrow(() -> new RuntimeException("Product not found with ID: " + uuid));
        } catch (IllegalArgumentException e) {
            return productRepository.findByProductCodeIgnoreCase(trimmed)
                    .orElseThrow(() -> new RuntimeException("Product not found with code: " + trimmed));
        }
    }

    public ProductResponse getProductByIdentifier(String identifier) {
        return toResponse(findProductByIdentifier(identifier));
    }

    private String generateProductCode() {
        int nextSeq = productRepository.findMaxProductSequence() + 1;
        return String.format("PROD-%05d", nextSeq);
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findByActiveTrue()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public org.springframework.data.domain.Page<ProductResponse> getAllProducts(org.springframework.data.domain.Pageable pageable) {
        return productRepository.findByActiveTrue(pageable)
                .map(this::toResponse);
    }

    public org.springframework.data.domain.Page<ProductResponse> getFilteredProductsPaged(
            int page, int size, String search, String categoryStr) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        
        Category category = null;
        String otherCategory = null;
        
        if (categoryStr != null && !categoryStr.isBlank() && !"ALL".equalsIgnoreCase(categoryStr)) {
            if (categoryStr.startsWith("OTHER:")) {
                category = Category.OTHER;
                otherCategory = categoryStr.substring(6).trim();
            } else {
                try {
                    category = Category.valueOf(categoryStr.toUpperCase().trim());
                } catch (IllegalArgumentException e) {
                    // Fallback to Category.OTHER or ignore
                }
            }
        }
        
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        
        return productRepository.findProductsFiltered(cleanSearch, category, otherCategory, pageable)
                .map(this::toResponse);
    }

    public ProductResponse getProductById(UUID id) {
        return toResponse(productRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Product not found: " + id)));
    }

    public List<ProductResponse> searchProducts(String name) {

        // Trim whitespace
        if (name == null || name.trim().isBlank()) {
            throw new RuntimeException(
                    "Search term cannot be blank");
        }

        return productRepository
                .findByNameContainingIgnoreCaseAndActiveTrue(
                        name.trim())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public org.springframework.data.domain.Page<ProductResponse> searchProducts(String name, org.springframework.data.domain.Pageable pageable) {
        if (name == null || name.trim().isBlank()) {
            throw new RuntimeException("Search term cannot be blank");
        }
        return productRepository
                .findByNameContainingIgnoreCaseAndActiveTrue(name.trim(), pageable)
                .map(this::toResponse);
    }

    public List<ProductResponse> getLowStockProducts() {
        return productRepository.findLowStockProducts()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ProductResponse createProduct(
            CreateProductRequest req) {

        // Trim and validate name
        String name = req.getName().trim();
        if (name.isBlank()) {
            throw new RuntimeException(
                    "Product name cannot be blank");
        }

        String brand = req.getBrand() != null ? req.getBrand().trim() : null;

        // Check duplicate product name
        boolean exists = productRepository.existsByNameIgnoreCase(name);
        if (exists) {
            throw new RuntimeException("Product already exists: " + name);
        }

        // Validate sell price > buy price
        if (req.getSellPricePrimary() != null
                && req.getSellPricePrimary()
                .compareTo(
                        req.getBuyPriceWithoutTax()) < 0) {
            throw new RuntimeException(
                    "Sell price cannot be less than buy price");
        }

        Product product = Product.builder()
                .name(name)
                .productCode(generateProductCode())
                .brand(req.getBrand() != null
                        ? req.getBrand().trim() : null)
                .category(req.getCategory())
                .otherCategoryDetail(req.getOtherCategoryDetail() != null ? req.getOtherCategoryDetail().trim() : null)
                .gstPercent(req.getGstPercent())
                .primaryUnit(req.getPrimaryUnit()
                        .toUpperCase().trim())
                .secondaryUnit(req.getSecondaryUnit()
                        .toUpperCase().trim())
                .secondaryPerPrimary(
                        req.getSecondaryPerPrimary())
                .canSellPrimary(req.getCanSellPrimary())
                .canSellSecondary(req.getCanSellSecondary())
                .buyPriceWithoutTax(
                        req.getBuyPriceWithoutTax())
                .cessPercent(req.getCessPercent() != null
                        ? req.getCessPercent() : BigDecimal.ZERO)
                .sellPricePrimary(req.getSellPricePrimary())
                .sellPriceSecondary(
                        req.getSellPriceSecondary())
                .lowStockAlert(req.getLowStockAlert())
                .lowStockUnit(req.getLowStockUnit())
                .active(true)
                .build();

        // Auto calculate buy price with tax
        product.calculateBuyPriceWithTax();

        return toResponse(productRepository.save(product));
    }

    public ProductResponse updateProduct(
            String idOrCode, CreateProductRequest req) {

        Product product = findProductByIdentifier(idOrCode);

        // Trim and validate
        String name = req.getName().trim();
        if (name.isBlank()) {
            throw new RuntimeException(
                    "Product name cannot be blank");
        }

        String brand = req.getBrand() != null ? req.getBrand().trim() : null;

        // Check duplicate product name excluding current product
        boolean exists = productRepository.existsByNameIgnoreCaseAndIdNot(name, product.getId());
        if (exists) {
            throw new RuntimeException("Product with similar name already exists: " + name);
        }

        // Validate sell price
        if (req.getSellPricePrimary() != null
                && req.getSellPricePrimary()
                .compareTo(req.getSellPricePrimary()) < 0) {
            throw new RuntimeException(
                    "Sell price cannot be less than buy price");
        }

        product.setName(name);
        product.setBrand(req.getBrand() != null
                ? req.getBrand().trim() : null);
        product.setCategory(req.getCategory());
        product.setOtherCategoryDetail(req.getOtherCategoryDetail() != null ? req.getOtherCategoryDetail().trim() : null);
        product.setGstPercent(req.getGstPercent());
        product.setCessPercent(req.getCessPercent() != null
                ? req.getCessPercent() : BigDecimal.ZERO);
        product.setBuyPriceWithoutTax(req.getBuyPriceWithoutTax());
        product.setSellPricePrimary(req.getSellPricePrimary());
        product.setSellPriceSecondary(req.getSellPriceSecondary());
        product.setLowStockAlert(req.getLowStockAlert());
        product.setPrimaryUnit(req.getPrimaryUnit());
        product.setSecondaryUnit(req.getSecondaryUnit());
        product.setSecondaryPerPrimary(req.getSecondaryPerPrimary());
        product.setCanSellPrimary(req.getCanSellPrimary());
        product.setCanSellSecondary(req.getCanSellSecondary());
        product.setLowStockUnit(req.getLowStockUnit());

        return toResponse(productRepository.save(product));
    }

    public void deleteProduct(String idOrCode) {
        Product product = findProductByIdentifier(idOrCode);
        product.setActive(false);
        productRepository.save(product);
    }
}