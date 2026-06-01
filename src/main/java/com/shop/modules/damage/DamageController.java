package com.shop.modules.damage;

import com.shop.common.ApiResponse;
import com.shop.modules.damage.dto.DamageResponse;
import com.shop.modules.damage.dto.LogDamageRequest;
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
@RequestMapping("/api/damage")
@RequiredArgsConstructor
public class DamageController {

    private final DamageService damageService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<DamageResponse> getAll() {
        return damageService.getAllDamageLogs();
    }

    @GetMapping("/report")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<DamageResponse> getMonthReport(
            @RequestParam int year,
            @RequestParam int month) {
        return damageService.getMonthReport(year, month);
    }

    @GetMapping("/total-loss")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public BigDecimal getTotalLoss(
            @RequestParam int year,
            @RequestParam int month) {
        return damageService.getMonthTotalLoss(year, month);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public DamageResponse log(
            @Valid @RequestBody LogDamageRequest req,
            Authentication auth) {
        return damageService.logDamage(req, auth.getName());
    }

    // ── Delete damage log — ADMIN only ──
    // Restores stock that was deducted when this damage was logged.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> deleteDamageLog(
            @PathVariable UUID id) {
        damageService.deleteDamageLog(id);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Damage log deleted and stock restored", null));
    }

    // ── Update damage log — ADMIN/MANAGER only ──
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<DamageResponse>> updateDamageLog(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDamageLogRequest req) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Damage log updated successfully",
                        damageService.updateDamageLog(
                                id,
                                req.getClaimStatus(),
                                req.getNotes())));
    }

    @lombok.Data
    public static class UpdateDamageLogRequest {
        private ClaimStatus claimStatus;
        private String notes;
    }
}