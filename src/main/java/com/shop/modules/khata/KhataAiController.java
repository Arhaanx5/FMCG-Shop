package com.shop.modules.khata;

import com.shop.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/khata/ai")
@RequiredArgsConstructor
public class KhataAiController {

    private final KhataAiService khataAiService;

    @GetMapping("/generate-reminder")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','SALESMAN','DELIVERY_BOY')")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateReminder(
            @RequestParam UUID customerId,
            @RequestParam(defaultValue = "HINGLISH") String language) {
        String draft = khataAiService.generateReminder(customerId, language);
        Map<String, String> result = new HashMap<>();
        result.put("draft", draft);
        return ResponseEntity.ok(ApiResponse.success("Reminder draft generated", result));
    }
}
