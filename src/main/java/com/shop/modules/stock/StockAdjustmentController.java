package com.shop.modules.stock;

import com.shop.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/stock/adjustment")
@RequiredArgsConstructor
public class StockAdjustmentController {

    private final StockAdjustmentService adjustmentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Void>> adjustStock(
            @Valid @RequestBody PhysicalAdjustmentRequest req,
            Principal principal) {
        
        String username = principal != null ? principal.getName() : "System";
        adjustmentService.adjustPhysicalStock(req.getBatchId(), req.getPhysicalCount(), req.getReason(), username);
        return ResponseEntity.ok(ApiResponse.success("Stock adjusted successfully", null));
    }

    @Data
    public static class PhysicalAdjustmentRequest {
        @NotNull(message = "Batch ID is required")
        private UUID batchId;

        @NotNull(message = "Physical count is required")
        @Min(value = 0, message = "Physical count must be at least 0")
        private Integer physicalCount;

        @NotBlank(message = "Reason is required")
        private String reason;
    }
}
