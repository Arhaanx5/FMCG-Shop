package com.shop.modules.hsnmapping;

import com.shop.common.ApiResponse;
import com.shop.modules.hsnmapping.dto.HsnCategoryMappingRequest;
import com.shop.modules.hsnmapping.dto.HsnCategoryMappingResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/hsn-mapping")
@RequiredArgsConstructor
public class HsnCategoryMappingController {

    private final HsnCategoryMappingService mappingService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<HsnCategoryMappingResponse>>> getAllMappings() {
        return ResponseEntity.ok(ApiResponse.success("Mappings fetched successfully", mappingService.getAllMappings()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<HsnCategoryMappingResponse>> saveMapping(
            @Valid @RequestBody HsnCategoryMappingRequest req,
            Authentication auth
    ) {
        return ResponseEntity.ok(ApiResponse.success("Mapping saved successfully", mappingService.saveMapping(req, auth.getName())));
    }

    @GetMapping("/live-categories")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<String>>> getLiveCategories(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        return ResponseEntity.ok(ApiResponse.success("Live categories fetched successfully", mappingService.getLiveCategories(start, end)));
    }

    @PostMapping("/apply")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> applyMapping(Authentication auth) {
        mappingService.applyMapping(auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Mappings applied successfully to products", null));
    }
}
