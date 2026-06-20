package com.shop.modules.stock;

import com.shop.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/stock/bi")
@RequiredArgsConstructor
public class StockBIController {

    private final StockBIService biService;

    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<StockBIService.HealthScoreBreakdown>> getHealthScore() {
        StockBIService.HealthScoreBreakdown score = biService.calculateHealthScore();
        return ResponseEntity.ok(ApiResponse.success(score));
    }

    @GetMapping("/reorder")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<StockBIService.ReorderSuggestion>>> getReorderSuggestions() {
        List<StockBIService.ReorderSuggestion> data = biService.getReorderSuggestions();
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
