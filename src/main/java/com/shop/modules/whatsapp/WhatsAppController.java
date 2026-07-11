package com.shop.modules.whatsapp;

import com.shop.common.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customers/whatsapp")
@RequiredArgsConstructor
public class WhatsAppController {

    private final WhatsAppService whatsAppService;

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, String>>> getWhatsAppStatus() {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Status fetched successfully",
                        Map.of("status", whatsAppService.getStatus())
                )
        );
    }

    @GetMapping("/qr")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, String>>> getWhatsAppQr() {
        String qr = whatsAppService.getQrCode();
        return ResponseEntity.ok(
                ApiResponse.success(
                        "QR Code fetched successfully",
                        Map.of("qr", qr != null ? qr : "")
                )
        );
    }

    @PostMapping("/logout")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<String>> logoutWhatsApp() {
        whatsAppService.logout();
        return ResponseEntity.ok(
                ApiResponse.success("Successfully logged out WhatsApp session", null)
        );
    }

    @PostMapping("/send-bulk")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<String>> sendBulkReminders(
            @RequestBody List<String> customerIds,
            Authentication auth) {
        String senderPhone = auth != null ? auth.getName() : null;
        whatsAppService.startBulkSending(customerIds, senderPhone);
        return ResponseEntity.ok(
                ApiResponse.success("Background bulk reminders initiated successfully", null)
        );
    }

    @GetMapping("/bulk-progress")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBulkProgress() {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Bulk progress fetched successfully",
                        whatsAppService.getProgress()
                )
        );
    }

    @PostMapping("/send-media")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<String>> sendWhatsAppMedia(@RequestBody WhatsAppMediaRequest req) {
        whatsAppService.sendMedia(req.getPhone(), req.getMedia(), req.getFilename(), req.getCaption());
        return ResponseEntity.ok(
                ApiResponse.success("WhatsApp media sent successfully", null)
        );
    }

    @PostMapping("/send-text")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<String>> sendWhatsAppText(@RequestBody WhatsAppTextRequest req) {
        whatsAppService.sendText(req.getPhone(), req.getMessage());
        return ResponseEntity.ok(
                ApiResponse.success("WhatsApp text message sent successfully", null)
        );
    }

    @PostMapping("/generate-pdf")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','SALESMAN','DELIVERY_BOY')")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateInvoicePdf(
            @RequestBody GeneratePdfRequest req) {
        if (req.getHtml() == null || req.getHtml().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("HTML content is required"));
        }
        String pdfBase64 = whatsAppService.generateInvoicePdf(req.getHtml());
        return ResponseEntity.ok(
                ApiResponse.success("PDF generated successfully",
                        Map.of("pdf", pdfBase64))
        );
    }

    @Data
    public static class WhatsAppMediaRequest {
        private String phone;
        private String media;
        private String filename;
        private String caption;
    }

    @Data
    public static class WhatsAppTextRequest {
        private String phone;
        private String message;
    }

    @Data
    public static class GeneratePdfRequest {
        private String html;
    }
}
