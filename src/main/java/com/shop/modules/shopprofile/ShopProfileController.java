package com.shop.modules.shopprofile;

import com.shop.common.ApiResponse;
import com.shop.modules.shopprofile.dto.ShopProfileResponse;
import com.shop.modules.shopprofile.dto.UpdateShopProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shop-profile")
@RequiredArgsConstructor
public class ShopProfileController {

    private final ShopProfileService shopProfileService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ShopProfileResponse>> getProfile() {
        return ResponseEntity.ok(ApiResponse.success("Shop profile fetched successfully", shopProfileService.getProfile()));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ShopProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateShopProfileRequest req,
            Authentication auth
    ) {
        return ResponseEntity.ok(ApiResponse.success("Shop profile updated successfully", shopProfileService.updateProfile(req, auth.getName())));
    }
}
