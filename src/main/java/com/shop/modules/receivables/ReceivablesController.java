package com.shop.modules.receivables;

import com.shop.common.ApiResponse;
import com.shop.modules.receivables.dto.ReceivablesPendingResponse;
import com.shop.modules.receivables.dto.SendReminderResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/receivables")
@RequiredArgsConstructor
public class ReceivablesController {

    private final ReceivablesService receivablesService;

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<ReceivablesPendingResponse>>> getPendingReceivables(
            @RequestParam(required = false, defaultValue = "daysOverdue") String sortBy) {
        return ResponseEntity.ok(ApiResponse.success(
                "Pending receivables fetched successfully",
                receivablesService.getPendingReceivables(sortBy)
        ));
    }

    @PostMapping("/{customerId}/send-reminder")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<SendReminderResponse>> sendReminder(
            @PathVariable UUID customerId,
            @RequestParam(required = false, defaultValue = "WHATSAPP") String channel,
            @RequestParam(required = false, defaultValue = "false") boolean ignoreCooldown,
            @RequestBody(required = false) ReminderRequest req,
            Authentication auth) {
        String notes = req != null ? req.getNotes() : "";
        String senderPhone = auth != null ? auth.getName() : null;
        return ResponseEntity.ok(ApiResponse.success(
                "Reminder process executed successfully",
                receivablesService.sendReminder(customerId, channel, notes, senderPhone, ignoreCooldown)
        ));
    }

    @Data
    public static class ReminderRequest {
        private String notes;
    }
}
