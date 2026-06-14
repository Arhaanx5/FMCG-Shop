package com.shop.modules.delivery;

import com.shop.common.ApiResponse;
import com.shop.modules.delivery.RouteOptimizationService.RouteResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final RouteOptimizationService routeOptimizationService;
    private final DeliveryRepository deliveryRepository;

    /**
     * GET /api/deliveries — list all deliveries (Admin/Manager sees all, Delivery
     * Boy/Salesman sees own)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY','SALESMAN')")
    public ResponseEntity<ApiResponse<List<DeliveryResponse>>> getAll(Authentication auth) {
        boolean isDeliveryBoyOrSalesman = auth.getAuthorities().stream()
                .anyMatch(
                        a -> a.getAuthority().equals("ROLE_DELIVERY_BOY") || a.getAuthority().equals("ROLE_SALESMAN"));

        List<Delivery> deliveries;
        if (isDeliveryBoyOrSalesman) {
            UUID userId = UUID.fromString(auth.getDetails().toString());
            deliveries = deliveryRepository.findByDeliveryBoyIdOrderByCreatedAtDesc(userId);
        } else {
            deliveries = deliveryRepository.findAllByOrderByCreatedAtDesc();
        }

        List<DeliveryResponse> responses = deliveries.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * GET /api/deliveries/stats — summary stats for the deliveries page
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY','SALESMAN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats(Authentication auth) {
        boolean isDeliveryBoyOrSalesman = auth.getAuthorities().stream()
                .anyMatch(
                        a -> a.getAuthority().equals("ROLE_DELIVERY_BOY") || a.getAuthority().equals("ROLE_SALESMAN"));

        Map<String, Object> stats = new HashMap<>();
        if (isDeliveryBoyOrSalesman) {
            UUID userId = UUID.fromString(auth.getDetails().toString());
            stats.put("pending", deliveryRepository.countByDeliveryBoyIdAndStatus(userId, DeliveryStatus.PENDING));
            stats.put("packed", deliveryRepository.countByDeliveryBoyIdAndStatus(userId, DeliveryStatus.PACKED));
            stats.put("out", deliveryRepository.countByDeliveryBoyIdAndStatus(userId, DeliveryStatus.OUT));
            stats.put("delivered", deliveryRepository.countByDeliveryBoyIdAndStatus(userId, DeliveryStatus.DELIVERED));
        } else {
            stats.put("pending", deliveryRepository.countByStatus(DeliveryStatus.PENDING));
            stats.put("packed", deliveryRepository.countByStatus(DeliveryStatus.PACKED));
            stats.put("out", deliveryRepository.countByStatus(DeliveryStatus.OUT));
            stats.put("delivered", deliveryRepository.countByStatus(DeliveryStatus.DELIVERED));
        }
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * POST /api/deliveries/assign — assign a bill to a delivery boy
     */
    @PostMapping("/assign")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<DeliveryResponse>> assign(
            @RequestBody DeliveryService.AssignDeliveryRequest req) {
        Delivery d = deliveryService.assignDelivery(req);
        return ResponseEntity.ok(ApiResponse.success("Delivery assigned", toResponse(d)));
    }

    /**
     * PUT /api/deliveries/{id}/status — update delivery status
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY','SALESMAN')")
    public ResponseEntity<ApiResponse<DeliveryResponse>> updateStatus(
            @PathVariable UUID id,
            @RequestBody StatusUpdateRequest req,
            Authentication auth) {
        boolean isRestricted = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DELIVERY_BOY") || a.getAuthority().equals("ROLE_SALESMAN"));
        if (isRestricted) {
            UUID userId = UUID.fromString(auth.getDetails().toString());
            Delivery d = deliveryRepository.findById(id).orElse(null);
            if (d == null) {
                return ResponseEntity.status(404).body(ApiResponse.error("Delivery not found"));
            }
            if (d.getDeliveryBoy() == null || !d.getDeliveryBoy().getId().equals(userId)) {
                return ResponseEntity.status(403).body(ApiResponse.error("Access denied: You are not assigned to this delivery"));
            }
        }
        Delivery d = deliveryService.updateStatus(id, req.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Status updated", toResponse(d)));
    }

    /**
     * GET /api/deliveries/route/{deliveryBoyId} — get optimized route for a
     * delivery boy (Admin/Manager view)
     */
    @GetMapping("/route/{deliveryBoyId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<RouteResult>> getRoute(
            @PathVariable UUID deliveryBoyId) {
        RouteResult result = routeOptimizationService.optimizeRoute(deliveryBoyId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * GET /api/deliveries/my-route — get optimized route for the logged-in delivery
     * boy
     */
    @GetMapping("/my-route")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY','SALESMAN')")
    public ResponseEntity<ApiResponse<RouteResult>> getMyRoute(Authentication auth) {
        UUID userId = UUID.fromString(auth.getDetails().toString());
        RouteResult result = routeOptimizationService.optimizeRoute(userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ─── Response DTO ──────────────────────────────────────

    private DeliveryResponse toResponse(Delivery d) {
        DeliveryResponse r = new DeliveryResponse();
        r.setId(d.getId());
        r.setBillNumber(d.getBill() != null ? d.getBill().getBillNumber() : null);
        r.setCustomerName(d.getBill() != null && d.getBill().getCustomer() != null
                ? d.getBill().getCustomer().getName()
                : null);
        r.setShopName(d.getBill() != null && d.getBill().getCustomer() != null
                ? d.getBill().getCustomer().getShopName()
                : null);
        r.setAreaName(d.getBill() != null && d.getBill().getCustomer() != null
                && d.getBill().getCustomer().getArea() != null
                        ? d.getBill().getCustomer().getArea().getName()
                        : null);
        r.setDeliveryBoyName(d.getDeliveryBoy() != null ? d.getDeliveryBoy().getName() : null);
        r.setDeliveryBoyId(d.getDeliveryBoy() != null ? d.getDeliveryBoy().getId() : null);
        r.setType(d.getType() != null ? d.getType().name() : null);
        r.setStatus(d.getStatus() != null ? d.getStatus().name() : null);
        r.setScheduledDate(d.getScheduledDate());
        r.setAmount(d.getBill() != null ? d.getBill().getGrandTotal() : null);
        r.setPendingAmount(d.getBill() != null ? d.getBill().getPendingAmount() : null);
        r.setCashCollected(d.getCashCollected());
        r.setDispatchedAt(d.getDispatchedAt());
        r.setDeliveredAt(d.getDeliveredAt());
        r.setCreatedAt(d.getCreatedAt());
        return r;
    }

    @Data
    public static class DeliveryResponse {
        private UUID id;
        private String billNumber;
        private String customerName;
        private String shopName;
        private String areaName;
        private String deliveryBoyName;
        private UUID deliveryBoyId;
        private String type;
        private String status;
        private LocalDate scheduledDate;
        private java.math.BigDecimal amount;
        private java.math.BigDecimal pendingAmount;
        private java.math.BigDecimal cashCollected;
        private java.time.LocalDateTime dispatchedAt;
        private java.time.LocalDateTime deliveredAt;
        private java.time.LocalDateTime createdAt;
    }

    @Data
    public static class StatusUpdateRequest {
        private DeliveryStatus status;
    }
}
