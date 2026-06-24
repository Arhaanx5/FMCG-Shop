package com.shop.modules.stock;

import com.shop.common.ApiResponse;
import com.shop.modules.backup.BackupService;
import com.shop.modules.billing.SoftReserveScheduler;
import com.shop.modules.delivery.CODReconciliationScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/scheduler")
@RequiredArgsConstructor
public class SchedulerController {

    @Autowired(required = false)
    private ExpiryScheduler expiryScheduler;

    @Autowired(required = false)
    private BackupService backupService;

    @Autowired(required = false)
    private SoftReserveScheduler softReserveScheduler;

    @Autowired(required = false)
    private CODReconciliationScheduler codReconciliationScheduler;

    // ── GET all schedulers status ──
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatuses() {
        Map<String, Object> statuses = new LinkedHashMap<>();

        statuses.put("expiry", Map.of(
            "name", "Stock Expiry Sweep",
            "status", expiryScheduler != null ? expiryScheduler.getStatus() : Map.of("enabled", false, "message", "Disabled")
        ));

        Map<String, Object> backupInfo = new LinkedHashMap<>();
        backupInfo.put("name", "Database Backup & Drive Upload");
        backupInfo.put("status", backupService != null ? backupService.getStatus() : Map.of("enabled", false, "message", "Disabled"));
        backupInfo.put("driveFolderId", backupService != null ? backupService.getDriveFolderId() : "");
        statuses.put("backup", backupInfo);

        statuses.put("softReserve", Map.of(
            "name", "Soft Reservation Release Sweep",
            "status", softReserveScheduler != null ? softReserveScheduler.getStatus() : Map.of("enabled", false, "message", "Disabled")
        ));

        statuses.put("codReconciliation", Map.of(
            "name", "COD Reconciliation & Escalation Sweep",
            "status", codReconciliationScheduler != null ? codReconciliationScheduler.getStatus() : Map.of("enabled", false, "message", "Disabled")
        ));

        return ResponseEntity.ok(ApiResponse.success("Schedulers status retrieved", statuses));
    }

    // ── POST run expiry sweep now ──
    @PostMapping("/expiry/run-now")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> runExpiryNow() {
        if (expiryScheduler == null) {
            return ResponseEntity.ok(ApiResponse.success("Expiry Scheduler is disabled in configurations.", null));
        }
        int count = expiryScheduler.manualRunNow();
        return ResponseEntity.ok(ApiResponse.success(
                "Expiry sweep completed. " + count + " expired batch(es) written off to Damage Logs.", null));
    }

    // ── POST run backup now ──
    @PostMapping("/backup/run-now")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> runBackupNow() {
        if (backupService == null) {
            return ResponseEntity.ok(ApiResponse.success("Backup Service is disabled in configurations.", Map.of("status", "DISABLED")));
        }
        Map<String, String> result = backupService.runManualBackup();
        if ("SUCCESS".equals(result.get("status"))) {
            return ResponseEntity.ok(ApiResponse.success("Manual database backup completed successfully", result));
        } else {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Backup failed: " + result.get("error")));
        }
    }

    // ── POST update backup config (Drive Folder ID) ──
    @PostMapping("/backup/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> updateBackupConfig(@RequestParam String driveFolderId) {
        if (backupService == null) {
            return ResponseEntity.ok(ApiResponse.success("Backup Service is disabled in configurations.", null));
        }
        try {
            backupService.saveCustomDriveFolderId(driveFolderId);
            return ResponseEntity.ok(ApiResponse.success("Google Drive Folder ID updated successfully", driveFolderId));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to update Google Drive Folder ID: " + e.getMessage()));
        }
    }

    // ── POST run soft-reserve sweep now ──
    @PostMapping("/soft-reserve/run-now")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> runSoftReserveNow() {
        if (softReserveScheduler == null) {
            return ResponseEntity.ok(ApiResponse.success("Soft Reserve Scheduler is disabled in configurations.", null));
        }
        int count = softReserveScheduler.runSweep();
        return ResponseEntity.ok(ApiResponse.success(
                "Soft reserve cleanup sweep completed. " + count + " expired draft bill(s) cancelled and released.", null));
    }

    // ── POST run COD EOD now ──
    @PostMapping("/cod-reconciliation/run-now")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> runCodReconciliationNow() {
        if (codReconciliationScheduler == null) {
            return ResponseEntity.ok(ApiResponse.success("COD Reconciliation Scheduler is disabled in configurations.", null));
        }
        codReconciliationScheduler.runEodNow();
        return ResponseEntity.ok(ApiResponse.success("COD EOD reconciliation report generation triggered successfully.", null));
    }

    // ── POST run COD Escalation now ──
    @PostMapping("/cod-escalation/run-now")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> runCodEscalationNow() {
        if (codReconciliationScheduler == null) {
            return ResponseEntity.ok(ApiResponse.success("COD Reconciliation Scheduler is disabled in configurations.", null));
        }
        codReconciliationScheduler.runEscalationNow();
        return ResponseEntity.ok(ApiResponse.success("COD outstanding deliveries escalation check completed.", null));
    }
}
