package com.shop.modules.delivery;

import com.shop.common.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reconciliations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    
    @GetMapping("/expected")
    public ResponseEntity<ApiResponse<BigDecimal>> getExpected(
            @RequestParam UUID deliveryBoyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        BigDecimal expected = reconciliationService.calculateExpectedCollectionForBoyAndDate(deliveryBoyId, targetDate);
        return ResponseEntity.ok(ApiResponse.success(expected));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DailyReconciliation>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(reconciliationService.getAllReconciliations()));
    }

    @GetMapping("/date/{date}")
    public ResponseEntity<ApiResponse<List<DailyReconciliation>>> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(reconciliationService.getReconciliationsByDate(date)));
    }

    @GetMapping("/boy/{boyId}")
    public ResponseEntity<ApiResponse<List<DailyReconciliation>>> getByBoy(@PathVariable UUID boyId) {
        return ResponseEntity.ok(ApiResponse.success(reconciliationService.getBoyReconciliations(boyId)));
    }

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<DailyReconciliation>> submit(@RequestBody SubmitCollectionRequest req) {
        LocalDate date = req.getDate() != null ? req.getDate() : LocalDate.now();
        DailyReconciliation recon = reconciliationService.submitCollection(
                req.getDeliveryBoyId(), date, req.getSubmittedCollection(), req.getAdminNotes());
        return ResponseEntity.ok(ApiResponse.success("Collection submitted successfully", recon));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<DailyReconciliation>> updateStatus(
            @PathVariable UUID id, @RequestBody UpdateStatusRequest req) {
        DailyReconciliation recon = reconciliationService.updateReconciliationStatus(id, req.getStatus(), req.getAdminNotes());
        return ResponseEntity.ok(ApiResponse.success("Reconciliation status updated", recon));
    }

    @Data
    public static class SubmitCollectionRequest {
        private UUID deliveryBoyId;
        private LocalDate date;
        private BigDecimal submittedCollection;
        private String adminNotes;
    }

    @Data
    public static class UpdateStatusRequest {
        private String status;
        private String adminNotes;
    }
}
