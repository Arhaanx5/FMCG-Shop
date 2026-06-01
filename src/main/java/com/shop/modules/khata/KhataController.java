package com.shop.modules.khata;

import com.shop.common.ApiResponse;
import com.shop.modules.khata.dto.PaymentResponse;
import com.shop.modules.khata.dto.RecordPaymentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
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
                .anyMatch(a -> a.getAuthority().equals("ROLE_DELIVERY_BOY") || a.getAuthority().equals("ROLE_SALESMAN"));

        if (isDeliveryBoyOrSalesman) {
            UUID userId = UUID.fromString(auth.getDetails().toString());
            return khataService.getCollectedByPayments(userId);
        }
        return khataService.getAllPayments();
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<PaymentResponse> getCustomerPayments(
            @PathVariable UUID customerId) {
        return khataService.getCustomerPayments(customerId);
    }

    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY','SALESMAN')")
    public List<PaymentResponse> getToday(Authentication auth) {
        boolean isDeliveryBoyOrSalesman = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DELIVERY_BOY") || a.getAuthority().equals("ROLE_SALESMAN"));

        if (isDeliveryBoyOrSalesman) {
            UUID userId = UUID.fromString(auth.getDetails().toString());
            return khataService.getTodayCollectedByPayments(userId);
        }
        return khataService.getTodayCollections();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY','SALESMAN')")
    public PaymentResponse record(
            @Valid @RequestBody RecordPaymentRequest req,
            Authentication auth) {
        return khataService.recordPayment(req, auth.getName());
    }

    // ── Delete payment — ADMIN only ──
    // Reverses customer pending balance and linked bill amounts.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> deletePayment(
            @PathVariable UUID id) {
        khataService.deletePayment(id);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Payment deleted and balances reversed", null));
    }

    // ── Update payment details — ADMIN/MANAGER only ──
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<PaymentResponse>> updatePayment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePaymentRequest req) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Payment updated successfully",
                        khataService.updatePayment(
                                id,
                                req.getPaymentMode(),
                                req.getNotes())));
    }

    @lombok.Data
    public static class UpdatePaymentRequest {
        private String paymentMode;
        private String notes;
    }
}