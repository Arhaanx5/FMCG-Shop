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

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY','SALESMAN')")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<CustomerResponse>>>
    getAll(@RequestParam(required = false) String search,
           @RequestParam(defaultValue = "0") int page,
           @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        if (search != null && !search.isBlank()) {
            return ResponseEntity.ok(
                    ApiResponse.success(
                            customerService.searchCustomers(search, pageable)));
        }
        return ResponseEntity.ok(
                ApiResponse.success(
                        customerService.getAllCustomers(pageable)));
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
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY')")
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