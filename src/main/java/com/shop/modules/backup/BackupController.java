package com.shop.modules.backup;

import com.shop.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    /**
     * Manually trigger a database backup and upload to Google Drive.
     * Only ADMIN users can trigger this.
     */
    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> runBackup() {
        Map<String, String> result = backupService.runManualBackup();
        if ("SUCCESS".equals(result.get("status"))) {
            return ResponseEntity.ok(ApiResponse.success("Backup completed successfully", result));
        } else {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Backup failed: " + result.get("error")));
        }
    }

    /**
     * Decrypt a database backup file locally for restore testing.
     * Only ADMIN users can trigger this.
     */
    @PostMapping("/decrypt")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> decryptBackup(@RequestParam String fileName) {
        try {
            String decryptedPath = backupService.decryptBackupFile(fileName);
            return ResponseEntity.ok(ApiResponse.success("Backup decrypted successfully: " + decryptedPath, decryptedPath));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Decryption failed: " + e.getMessage()));
        }
    }

    /**
     * Get list of all available local backup files.
     * Only ADMIN users can view this.
     */
    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getBackupList() {
        return ResponseEntity.ok(ApiResponse.success("Available backup files retrieved", backupService.getAvailableBackups()));
    }
}
