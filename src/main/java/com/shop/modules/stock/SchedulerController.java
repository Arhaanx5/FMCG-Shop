package com.shop.modules.stock;

import com.shop.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/scheduler")
@RequiredArgsConstructor
public class SchedulerController {

    @Autowired(required = false)
    private ExpiryScheduler expiryScheduler;

    // ── GET scheduler status ──
    @GetMapping("/expiry/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> getStatus() {
        if (expiryScheduler == null) {
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("enabled", false, "message", "Scheduler is disabled in configuration")));
        }
        return ResponseEntity.ok(ApiResponse.success(expiryScheduler.getStatus()));
    }

    // ── POST run sweep now ──
    @PostMapping("/expiry/run-now")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> runNow() {
        if (expiryScheduler == null) {
            return ResponseEntity.ok(ApiResponse.success("Scheduler is disabled. Enable it in application.properties first.", null));
        }
        int count = expiryScheduler.manualRunNow();
        return ResponseEntity.ok(ApiResponse.success(
                "Expiry sweep completed. " + count + " expired batch(es) written off to Damage Logs.", null));
    }
}
