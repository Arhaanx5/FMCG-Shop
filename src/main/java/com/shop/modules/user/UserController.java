package com.shop.modules.user;

import com.shop.common.ApiResponse;
import com.shop.modules.user.dto.CreateUserRequest;
import com.shop.modules.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>>
    getAllUsers() {
        return ResponseEntity.ok(
                ApiResponse.success(
                        userService.getAllUsers()));
    }

    @GetMapping("/delivery-boys")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<UserResponse>>>
    getDeliveryBoys() {
        return ResponseEntity.ok(
                ApiResponse.success(
                        userService.getDeliveryBoys()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>>
    getById(@PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        userService.getUserById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>>
    create(@Valid @RequestBody CreateUserRequest req) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "User created successfully",
                        userService.createUser(req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>>
    update(@PathVariable UUID id,
           @Valid @RequestBody CreateUserRequest req) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "User updated successfully",
                        userService.updateUser(id, req)));
    }

    @PutMapping("/{id}/toggle-active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>>
    toggleActive(@PathVariable UUID id) {
        userService.toggleActive(id);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "User status toggled", null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>>
    delete(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "User deleted", null));
    }

    @PutMapping("/live-location")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY_BOY','SALESMAN')")
    public ResponseEntity<ApiResponse<String>> updateLiveLocation(
            @Valid @RequestBody LiveLocationRequest req,
            org.springframework.security.core.Authentication auth) {
        userService.updateLiveLocation(auth.getName(), req.getLatitude(), req.getLongitude());
        return ResponseEntity.ok(
                ApiResponse.success("Live location updated", null));
    }

    @lombok.Data
    public static class LiveLocationRequest {
        @jakarta.validation.constraints.NotNull(message = "Latitude is required")
        private Double latitude;
        @jakarta.validation.constraints.NotNull(message = "Longitude is required")
        private Double longitude;
    }
}