package com.shop.modules.shopprofile;

import com.shop.modules.shopprofile.dto.ShopProfileResponse;
import com.shop.modules.shopprofile.dto.UpdateShopProfileRequest;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShopProfileService {

    private final ShopProfileRepository shopProfileRepository;
    private final UserRepository userRepository;

    // Singleton Fixed ID
    public static final UUID SHOP_PROFILE_ID = UUID.fromString("d3b07384-d113-4ae0-91be-37a113c3d3de");

    public ShopProfileResponse getProfile() {
        ShopProfile profile = shopProfileRepository.findById(SHOP_PROFILE_ID)
                .orElseThrow(() -> new RuntimeException("Shop profile not found"));
        return toResponse(profile);
    }

    public ShopProfile getActiveProfileEntity() {
        return shopProfileRepository.findById(SHOP_PROFILE_ID)
                .orElseThrow(() -> new RuntimeException("Shop profile not found"));
    }

    @Transactional
    public ShopProfileResponse updateProfile(UpdateShopProfileRequest req, String username) {
        User user = userRepository.findByPhone(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        ShopProfile profile = shopProfileRepository.findById(SHOP_PROFILE_ID)
                .orElseGet(() -> ShopProfile.builder().id(SHOP_PROFILE_ID).build());

        profile.setCompanyName(req.getCompanyName().trim());
        profile.setGstin(req.getGstin().trim().toUpperCase());
        profile.setFssai(req.getFssai() != null && !req.getFssai().isBlank() ? req.getFssai().trim() : null);
        profile.setPhone(req.getPhone() != null ? req.getPhone().trim() : null);
        profile.setAddress(req.getAddress() != null ? req.getAddress().trim() : null);
        profile.setStateCode(req.getStateCode().trim());
        profile.setStateName(req.getStateName().trim());
        profile.setUpdatedBy(user.getId());
        profile.setUpdatedAt(LocalDateTime.now());

        ShopProfile saved = shopProfileRepository.save(profile);
        return toResponse(saved);
    }

    private ShopProfileResponse toResponse(ShopProfile profile) {
        String updatedByName = null;
        if (profile.getUpdatedBy() != null) {
            updatedByName = userRepository.findById(profile.getUpdatedBy())
                    .map(User::getName)
                    .orElse(null);
        }

        return ShopProfileResponse.builder()
                .id(profile.getId())
                .companyName(profile.getCompanyName())
                .gstin(profile.getGstin())
                .fssai(profile.getFssai())
                .phone(profile.getPhone())
                .address(profile.getAddress())
                .stateCode(profile.getStateCode())
                .stateName(profile.getStateName())
                .updatedBy(profile.getUpdatedBy())
                .updatedByName(updatedByName)
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
