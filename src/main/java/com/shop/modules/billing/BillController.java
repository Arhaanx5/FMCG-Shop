package com.shop.modules.billing;
import java.time.LocalDateTime;
import com.shop.common.ApiResponse;
import com.shop.modules.billing.dto.BillResponse;
import com.shop.modules.billing.dto.CreateBillRequest;
import com.shop.modules.billing.dto.ReturnItemsRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bills")
@RequiredArgsConstructor
public class BillController {

    private final BillService billService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<BillResponse>>>
    getAll() {
        return ResponseEntity.ok(
                ApiResponse.success(
                        billService.getAllBills()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<BillResponse>>
    getById(@PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        billService.getBillById(id)));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<BillResponse>>>
    getPending() {
        return ResponseEntity.ok(
                ApiResponse.success(
                        billService.getPendingBills()));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<BillResponse>>>
    getCustomerHistory(
            @PathVariable UUID customerId) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        billService.getCustomerHistory(
                                customerId)));
    }
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','SALESMAN')")
    public ResponseEntity<ApiResponse<BillResponse>>
    create(@Valid @RequestBody CreateBillRequest req,
           Authentication auth) {
        try {
            boolean isSalesman = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_SALESMAN") || a.getAuthority().equals("SALESMAN"));
            if (isSalesman && req.getStatus() != BillStatus.DRAFT) {
                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.<BillResponse>builder()
                                .success(false)
                                .message("Salesmen are restricted to booking DRAFT orders only.")
                                .data(null)
                                .timestamp(java.time.LocalDateTime.now())
                                .build());
            }
            return ResponseEntity.ok(
                    ApiResponse.success(
                            "Bill created successfully",
                            billService.createBill(
                                    req, auth.getName())));
        } catch (RuntimeException ex) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<BillResponse>builder()
                            .success(false)
                            .message(ex.getMessage())
                            .data(null)
                            .timestamp(java.time.LocalDateTime.now())
                            .build());
        }
    }
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<String>>
    cancel(@PathVariable UUID id) {
        billService.cancelBill(id);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Bill cancelled", null));
    }

    @PutMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<BillResponse>>
    returnItems(@PathVariable UUID id,
                @Valid @RequestBody ReturnItemsRequest req) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Items returned successfully",
                        billService.returnItems(id, req)));
    }

    // ── Delete bill — ADMIN only ──
    // Only CANCELLED bills can be hard-deleted (stock already restored).
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>>
    deleteBill(@PathVariable UUID id) {
        billService.deleteBill(id);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Cancelled bill deleted successfully", null));
    }

    // ── Update bill details — ADMIN/MANAGER only ──
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<BillResponse>>
    updateBill(@PathVariable UUID id,
               @Valid @RequestBody UpdateBillRequest req) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Bill updated successfully",
                        billService.updateBillDetails(
                                id,
                                req.getPaymentMode(),
                                req.getNotes())));
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<BillResponse>>
    confirmBill(@PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Bill confirmed and stock depleted",
                        billService.confirmBill(id)));
    }

    @PostMapping("/bulk-confirm")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<BillService.BulkConfirmResult>>>
    bulkConfirmBills(@RequestBody List<UUID> ids) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Bulk confirmation processing completed",
                        billService.bulkConfirmBills(ids)));
    }

    @lombok.Data
    public static class UpdateBillRequest {
        private PaymentMode paymentMode;
        private String notes;
    }
}