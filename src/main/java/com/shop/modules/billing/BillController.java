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
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','SALESMAN','DELIVERY_BOY')")
    public ResponseEntity<ApiResponse<com.shop.common.PagedResponse<BillResponse>>>
    getAll(
            @RequestParam(required = false) BillStatus status,
            @RequestParam(required = false) Boolean excludeDrafts,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            Authentication auth
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        billService.getBillsPaged(status, excludeDrafts, search, page, size, sort, auth.getName())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','SALESMAN','DELIVERY_BOY')")
    public ResponseEntity<ApiResponse<BillResponse>>
    getById(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        billService.getBillById(id, auth.getName())));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','SALESMAN','DELIVERY_BOY')")
    public ResponseEntity<ApiResponse<List<BillResponse>>>
    getPending() {
        return ResponseEntity.ok(
                ApiResponse.success(
                        billService.getPendingBills()));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','SALESMAN','DELIVERY_BOY')")
    public ResponseEntity<ApiResponse<com.shop.common.PagedResponse<BillResponse>>>
    getCustomerHistory(
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        billService.getCustomerHistoryPaged(customerId, page, size, auth.getName())));
    }
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','SALESMAN','DELIVERY_BOY')")
    public ResponseEntity<ApiResponse<BillResponse>>
    create(@Valid @RequestBody CreateBillRequest req,
           @RequestParam(required = false, defaultValue = "false") boolean overrideCost,
           Authentication auth) {
        try {
            boolean isRestricted = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_SALESMAN") || a.getAuthority().equals("SALESMAN")
                                || a.getAuthority().equals("ROLE_DELIVERY_BOY") || a.getAuthority().equals("DELIVERY_BOY"));
            if (isRestricted && req.getStatus() != BillStatus.DRAFT) {
                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.<BillResponse>builder()
                                .success(false)
                                .message("Salesmen and delivery boys are restricted to booking DRAFT orders only.")
                                .data(null)
                                .timestamp(java.time.LocalDateTime.now())
                                .build());
            }
            return ResponseEntity.ok(
                    ApiResponse.success(
                            "Bill created successfully",
                            billService.createBill(
                                    req, auth.getName(), overrideCost)));
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
    cancel(@PathVariable UUID id, Authentication auth) {
        billService.cancelBill(id, auth != null ? auth.getName() : "System");
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Bill cancelled", null));
    }

    @PutMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<BillResponse>>
    returnItems(@PathVariable UUID id,
                @Valid @RequestBody ReturnItemsRequest req,
                Authentication auth) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Items returned successfully",
                        billService.returnItems(id, req, auth != null ? auth.getName() : "System")));
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
               @Valid @RequestBody UpdateBillRequest req,
               @RequestParam(required = false, defaultValue = "false") boolean overrideCost,
               Authentication auth) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Bill updated successfully",
                        billService.updateBillDetails(
                                id,
                                req.getPaymentMode(),
                                req.getNotes(),
                                req.getStatus(),
                                req.getPaidAmount(),
                                req.getDiscount(),
                                req.getVersion(),
                                req.getEditReason(),
                                req.getItems(),
                                overrideCost,
                                req.getPartialPaymentMode(),
                                auth != null ? auth.getName() : "System")));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<BillEditHistory>>> getEditHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        billService.getBillEditHistory(id)));
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<BillResponse>>
    confirmBill(@PathVariable UUID id,
                @RequestParam(required = false, defaultValue = "false") boolean overrideCost,
                Authentication auth) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Bill confirmed and stock depleted",
                        billService.confirmBill(id, overrideCost, auth != null ? auth.getName() : "System")));
    }

    @PutMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<BillResponse>>
    restoreBill(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Bill restored successfully",
                        billService.restoreBill(id, auth != null ? auth.getName() : "System")));
    }


    @PostMapping("/bulk-confirm")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<BillService.BulkConfirmResult>>>
    bulkConfirmBills(@RequestBody List<UUID> ids, Authentication auth) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Bulk confirmation processing completed",
                        billService.bulkConfirmBills(ids, auth != null ? auth.getName() : "System")));
    }

    @lombok.Data
    public static class UpdateBillRequest {
        private PaymentMode paymentMode;
        private String notes;
        private BillStatus status;
        private java.math.BigDecimal paidAmount;
        private java.math.BigDecimal discount;
        private Integer version;
        private String editReason;
        private List<com.shop.modules.billing.dto.CreateBillRequest.BillItemRequest> items;
        private String partialPaymentMode;
    }
}