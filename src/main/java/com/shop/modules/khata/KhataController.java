package com.shop.modules.khata;

import com.shop.common.ApiResponse;
import com.shop.modules.khata.dto.OverpaymentPreviewResponse;
import com.shop.modules.khata.dto.PaymentResponse;
import com.shop.modules.khata.dto.RecordPaymentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class KhataController {

    private final KhataService khataService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY','SALESMAN')")
    public List<PaymentResponse> getAll(Authentication auth) {
        boolean isDeliveryBoyOrSalesman = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DELIVERY_BOY")
                        || a.getAuthority().equals("ROLE_SALESMAN"));
        if (isDeliveryBoyOrSalesman) {
            UUID userId = UUID.fromString(auth.getDetails().toString());
            return khataService.getCollectedByPayments(userId);
        }
        return khataService.getAllPayments();
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY','SALESMAN')")
    public List<PaymentResponse> getCustomerPayments(@PathVariable UUID customerId) {
        return khataService.getCustomerPayments(customerId);
    }

    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY','SALESMAN')")
    public List<PaymentResponse> getToday(Authentication auth) {
        boolean isDeliveryBoyOrSalesman = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DELIVERY_BOY")
                        || a.getAuthority().equals("ROLE_SALESMAN"));
        if (isDeliveryBoyOrSalesman) {
            UUID userId = UUID.fromString(auth.getDetails().toString());
            return khataService.getTodayCollectedByPayments(userId);
        }
        return khataService.getTodayCollections();
    }

    /**
     * Preview overpayment BEFORE saving — does NOT write anything to DB.
     * Returns null data if amount <= pending (normal payment, proceed directly).
     * Returns OverpaymentPreviewResponse if excess detected (show modal to user).
     */
    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY','SALESMAN')")
    public ResponseEntity<ApiResponse<OverpaymentPreviewResponse>> previewPayment(
            @RequestBody PreviewRequest req) {
        OverpaymentPreviewResponse preview = khataService.previewOverpayment(
                req.getCustomerId(), req.getBillId(), req.getAmount());
        if (preview == null) {
            return ResponseEntity.ok(ApiResponse.success("No overpayment", null));
        }
        return ResponseEntity.ok(ApiResponse.success("Overpayment detected", preview));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY','SALESMAN')")
    public ResponseEntity<ApiResponse<PaymentResponse>> record(
            @Valid @RequestBody RecordPaymentRequest req,
            Authentication auth) {
        if ("WAIVE_OFF".equalsIgnoreCase(req.getPaymentMode())) {
            boolean isAdminOrManager = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                            || a.getAuthority().equals("ROLE_MANAGER"));
            if (!isAdminOrManager) {
                return ResponseEntity
                        .status(org.springframework.http.HttpStatus.FORBIDDEN)
                        .body(ApiResponse.<PaymentResponse>builder()
                                .success(false)
                                .message("Waive-off payment sirf ADMIN ya MANAGER kar sakte hain. Aapke paas permission nahi hai.")
                                .data(null)
                                .timestamp(java.time.LocalDateTime.now())
                                .build());
            }
        }
        PaymentResponse response = khataService.recordPayment(req, auth.getName());
        return ResponseEntity.ok(ApiResponse.<PaymentResponse>builder()
                .success(true)
                .message("Payment recorded successfully")
                .data(response)
                .timestamp(java.time.LocalDateTime.now())
                .build());
    }

    // ── Delete payment — ADMIN only ──
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> deletePayment(@PathVariable UUID id) {
        khataService.deletePayment(id);
        return ResponseEntity.ok(
                ApiResponse.success("Payment deleted and balances reversed", null));
    }

    // ── Update payment details — ADMIN/MANAGER only ──
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<PaymentResponse>> updatePayment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePaymentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Payment updated successfully",
                khataService.updatePayment(id, req.getPaymentMode(), req.getNotes())));
    }

    @lombok.Data
    public static class UpdatePaymentRequest {
        private String paymentMode;
        private String notes;
    }

    @lombok.Data
    public static class PreviewRequest {
        private UUID customerId;
        private UUID billId;
        private BigDecimal amount;
    }
}