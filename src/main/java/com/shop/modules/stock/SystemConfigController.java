package com.shop.modules.stock;

import com.shop.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class SystemConfigController {

    @Value("${app.features.ai-enabled:true}")
    private boolean aiEnabled;

    @GetMapping("/features")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFeatures() {
        Map<String, Object> features = new HashMap<>();
        features.put("aiEnabled", aiEnabled);
        return ResponseEntity.ok(ApiResponse.success(features));
    }
}
