package com.shop.modules.billing.reports;

import com.shop.common.ApiResponse;
import com.shop.modules.billing.reports.dto.Gstr1ReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports/gstr1")
@RequiredArgsConstructor
public class Gstr1ReportController {

    private final Gstr1ReportService gstr1ReportService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Gstr1ReportResponse>> getGstr1Report(
            @RequestParam String month
    ) {
        return ResponseEntity.ok(ApiResponse.success("GSTR-1 report data fetched successfully", gstr1ReportService.generateGstr1Report(month)));
    }
}
