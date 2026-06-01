package com.shop.modules.product;

import com.shop.common.ApiResponse;
import com.shop.modules.product.dto.CreateProductRequest;
import com.shop.modules.product.dto.ProductResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<ProductResponse>>>
    getAll(@RequestParam(required = false) String search,
           @RequestParam(defaultValue = "0") int page,
           @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        if (search != null && !search.isBlank()) {
            return ResponseEntity.ok(
                    ApiResponse.success(
                            productService.searchProducts(search, pageable)));
        }
        return ResponseEntity.ok(
                ApiResponse.success(
                        productService.getAllProducts(pageable)));
    }

    @GetMapping("/{idOrCode}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<ProductResponse>>
    getById(@PathVariable String idOrCode) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        productService.getProductByIdentifier(idOrCode)));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<ProductResponse>>>
    getLowStock() {
        return ResponseEntity.ok(
                ApiResponse.success(
                        productService.getLowStockProducts()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<ProductResponse>>
    create(@Valid @RequestBody
           CreateProductRequest req) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Product created successfully",
                        productService.createProduct(req)));
    }

    @PutMapping("/{idOrCode}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<ProductResponse>>
    update(@PathVariable String idOrCode,
           @Valid @RequestBody
           CreateProductRequest req) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Product updated successfully",
                        productService.updateProduct(idOrCode, req)));
    }

    @DeleteMapping("/{idOrCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>>
    delete(@PathVariable String idOrCode) {
        productService.deleteProduct(idOrCode);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Product deactivated", null));
    }
}