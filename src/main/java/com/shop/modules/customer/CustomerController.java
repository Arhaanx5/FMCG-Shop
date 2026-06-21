package com.shop.modules.customer;

import com.shop.common.ApiResponse;
import com.shop.modules.customer.dto.CreateCustomerRequest;
import com.shop.modules.customer.dto.CustomerResponse;
import com.shop.modules.customer.dto.AiReminderResponse;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final AiReminderService aiReminderService;
    private final WhatsAppService whatsAppService;

    @GetMapping("/whatsapp/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> getWhatsAppStatus() {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Status fetched successfully",
                        java.util.Map.of("status", whatsAppService.getStatus())
                )
        );
    }

    @GetMapping("/whatsapp/qr")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> getWhatsAppQr() {
        String qr = whatsAppService.getQrCode();
        return ResponseEntity.ok(
                ApiResponse.success(
                        "QR Code fetched successfully",
                        java.util.Map.of("qr", qr != null ? qr : "")
                )
        );
    }

    @PostMapping("/whatsapp/logout")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<String>> logoutWhatsApp() {
        whatsAppService.logout();
        return ResponseEntity.ok(
                ApiResponse.success("Successfully logged out WhatsApp session", null)
        );
    }

    @PostMapping("/whatsapp/send-bulk")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<String>> sendBulkReminders(
            @RequestBody java.util.List<String> customerIds,
            org.springframework.security.core.Authentication auth) {
        String senderPhone = auth != null ? auth.getName() : null;
        whatsAppService.startBulkSending(customerIds, senderPhone);
        return ResponseEntity.ok(
                ApiResponse.success("Background bulk reminders initiated successfully", null)
        );
    }

    @GetMapping("/whatsapp/bulk-progress")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getBulkProgress() {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Bulk progress fetched successfully",
                        whatsAppService.getProgress()
                )
        );
    }

    @PostMapping("/whatsapp/send-media")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<String>> sendWhatsAppMedia(@RequestBody WhatsAppMediaRequest req) {
        whatsAppService.sendMedia(req.getPhone(), req.getMedia(), req.getFilename(), req.getCaption());
        return ResponseEntity.ok(
                ApiResponse.success("WhatsApp media sent successfully", null)
        );
    }

    @PostMapping("/whatsapp/send-text")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<String>> sendWhatsAppText(@RequestBody WhatsAppTextRequest req) {
        whatsAppService.sendText(req.getPhone(), req.getMessage());
        return ResponseEntity.ok(
                ApiResponse.success("WhatsApp text message sent successfully", null)
        );
    }

    @PostMapping("/whatsapp/generate-pdf")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','SALESMAN','DELIVERY_BOY')")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> generateInvoicePdf(
            @RequestBody GeneratePdfRequest req) {
        if (req.getHtml() == null || req.getHtml().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("HTML content is required"));
        }
        String pdfBase64 = whatsAppService.generateInvoicePdf(req.getHtml());
        return ResponseEntity.ok(
                ApiResponse.success("PDF generated successfully",
                        java.util.Map.of("pdf", pdfBase64))
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


    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY','SALESMAN')")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<CustomerResponse>>>
    getAll(@RequestParam(required = false) String search,
           @RequestParam(required = false) Boolean active,
           @RequestParam(defaultValue = "0") int page,
           @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        customerService.getFilteredCustomersPaged(page, size, search, active)));
    }

    @GetMapping("/{idOrCode}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY','SALESMAN')")
    public ResponseEntity<ApiResponse<CustomerResponse>>
    getById(@PathVariable String idOrCode) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        customerService.getCustomerByIdentifier(idOrCode)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<CustomerResponse>>
    create(@Valid @RequestBody
           CreateCustomerRequest req) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Customer created successfully",
                        customerService.createCustomer(req)));
    }

    @PutMapping("/{idOrCode}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<CustomerResponse>>
    update(@PathVariable String idOrCode,
           @Valid @RequestBody
           CreateCustomerRequest req) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Customer updated successfully",
                        customerService.updateCustomer(idOrCode, req)));
    }

    @PutMapping("/{idOrCode}/location")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY','SALESMAN')")
    public ResponseEntity<ApiResponse<CustomerResponse>>
    updateLocation(@PathVariable String idOrCode,
                   @RequestBody LocationRequest req) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Location updated successfully",
                        customerService.updateLocation(
                                idOrCode,
                                req.getLatitude(),
                                req.getLongitude(),
                                req.getMethod())));
    }

    @GetMapping("/inactive")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<CustomerResponse>>>
    getInactive() {
        return ResponseEntity.ok(
                ApiResponse.success(
                        customerService.getInactiveCustomers()));
    }

    @DeleteMapping("/{idOrCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>>
    deactivate(@PathVariable String idOrCode) {
        customerService.deactivateCustomer(idOrCode);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Customer deactivated", null));
    }

    @PostMapping("/scan-npa")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<String>> scanNpa() {
        customerService.scanAndMarkNpaCustomers();
        return ResponseEntity.ok(
                ApiResponse.success(
                        "NPA scan completed successfully", null));
    }

    @PostMapping("/{idOrCode}/reminder")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<AiReminderResponse>> getReminder(@PathVariable String idOrCode) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "WhatsApp reminder generated successfully",
                        aiReminderService.generateCustomerReminder(idOrCode)));
    }

    @Data
    static class LocationRequest {
        private Double latitude;
        private Double longitude;
        private String method;
    }
}