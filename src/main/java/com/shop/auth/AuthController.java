package com.shop.auth;

import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TotpUtil totpUtil;

    // ── Simple in-memory rate limiter (no external dep needed) ──
    // Tracks failed attempts per phone number
    private final ConcurrentHashMap<String, long[]> failedAttempts = new ConcurrentHashMap<>();
    private static final int MAX_ATTEMPTS    = 5;
    private static final long WINDOW_MS      = 15 * 60 * 1000L; // 15 minutes

    private boolean isRateLimited(String phone) {
        long now = System.currentTimeMillis();
        failedAttempts.compute(phone, (k, v) -> {
            if (v == null) return new long[]{0, now};
            // Reset window if expired
            if (now - v[1] > WINDOW_MS) return new long[]{0, now};
            return v;
        });
        long[] state = failedAttempts.get(phone);
        return state != null && state[0] >= MAX_ATTEMPTS
                && (System.currentTimeMillis() - state[1]) <= WINDOW_MS;
    }

    private void recordFailedAttempt(String phone) {
        long now = System.currentTimeMillis();
        failedAttempts.compute(phone, (k, v) -> {
            if (v == null || (now - v[1]) > WINDOW_MS) return new long[]{1, now};
            v[0]++;
            return v;
        });
    }

    private void clearFailedAttempts(String phone) {
        failedAttempts.remove(phone);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        // ── Rate limiting check ──────────────────────────────────
        if (req.getPhone() != null && isRateLimited(req.getPhone())) {
            log.warn("SECURITY: Rate limit exceeded for phone: {}", req.getPhone());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "Too many failed attempts. Please try again after 15 minutes."));
        }

        // ── Input validation: password max length to prevent BCrypt DoS ──
        if (req.getPassword() != null && req.getPassword().length() > 72) {
            recordFailedAttempt(req.getPhone());
            return ResponseEntity.status(401)
                .body(Map.of("error", "Invalid credentials"));
        }

        User user = userRepository.findByPhone(req.getPhone())
                .orElse(null);

        if (user == null || !user.getActive()) {
            recordFailedAttempt(req.getPhone() != null ? req.getPhone() : "unknown");
            log.warn("SECURITY: Failed login — user not found or inactive: {}", req.getPhone());
            return ResponseEntity.status(401)
                .body(Map.of("error", "Invalid credentials"));
        }

        if (!passwordEncoder.matches(req.getPassword(),
                user.getPasswordHash())) {
            recordFailedAttempt(req.getPhone());
            log.warn("SECURITY: Failed login — wrong password for: {}", req.getPhone());
            return ResponseEntity.status(401)
                .body(Map.of("error", "Invalid credentials"));
        }

        // ── Success ──────────────────────────────────────────────
        clearFailedAttempts(req.getPhone());
        log.info("SECURITY: Successful login for: {} (role: {})", req.getPhone(), user.getRole());

        // Check if MFA is enabled
        if (Boolean.TRUE.equals(user.getMfaEnabled()) && user.getRole() == com.shop.modules.user.UserRole.ADMIN) {
            String mfaToken = jwtUtil.generateMfaToken(user.getPhone(), user.getId().toString());
            return ResponseEntity.ok(Map.of(
                    "mfaRequired", true,
                    "mfaToken", mfaToken,
                    "phone", user.getPhone()
            ));
        }

        String token = jwtUtil.generateToken(
                user.getPhone(),
                user.getRole().name(),
                user.getId().toString()
        );

        return ResponseEntity.ok(Map.of(
                "token", token,
                "role", user.getRole(),
                "name", user.getName(),
                "userId", user.getId(),
                "mustChangePassword", user.getMustChangePassword()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader("Authorization") String header) {
        String token = header.substring(7);
        String phone = jwtUtil.getPhone(token);
        User user = userRepository.findByPhone(phone).orElseThrow();
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "phone", user.getPhone(),
                "role", user.getRole(),
                "mustChangePassword", user.getMustChangePassword(),
                "mfaEnabled", Boolean.TRUE.equals(user.getMfaEnabled())
        ));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestHeader("Authorization") String header,
            @jakarta.validation.Valid @RequestBody ChangePasswordRequest req) {

        // ── Password length guard (DoS prevention) ───────────────
        if (req.getCurrentPassword() != null && req.getCurrentPassword().length() > 72) {
            return ResponseEntity.status(400)
                .body(Map.of("error", "Invalid current password"));
        }
        if (req.getNewPassword() != null && req.getNewPassword().length() > 72) {
            return ResponseEntity.status(400)
                .body(Map.of("error", "New password must not exceed 72 characters"));
        }

        String token = header.substring(7);
        String phone = jwtUtil.getPhone(token);
        User user = userRepository.findByPhone(phone).orElseThrow();

        if (!passwordEncoder.matches(req.getCurrentPassword(),
                user.getPasswordHash())) {
            return ResponseEntity.status(400)
                .body(Map.of("error", "Current password is incorrect"));
        }

        user.setPasswordHash(
            passwordEncoder.encode(req.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        log.info("SECURITY: Password changed for: {}", phone);
        return ResponseEntity.ok(Map.of("message", "Password changed"));
    }

    @PostMapping("/mfa/setup")
    public ResponseEntity<?> setupMfa(@RequestHeader("Authorization") String header) {
        String token = header.substring(7);
        String phone = jwtUtil.getPhone(token);
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            return ResponseEntity.badRequest().body(Map.of("error", "MFA is already enabled"));
        }

        String secret = totpUtil.generateSecretKey();
        user.setMfaSecret(secret);
        userRepository.save(user);

        String qrCodeUrl = totpUtil.getQrCodeUrl(secret, user.getPhone());
        return ResponseEntity.ok(Map.of(
                "secret", secret,
                "qrCodeUrl", qrCodeUrl
        ));
    }

    @PostMapping("/mfa/enable")
    public ResponseEntity<?> enableMfa(
            @RequestHeader("Authorization") String header,
            @RequestBody Map<String, String> body) {
        String token = header.substring(7);
        String phone = jwtUtil.getPhone(token);
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String codeStr = body.get("code");
        if (codeStr == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MFA code is required"));
        }

        int code;
        try {
            code = Integer.parseInt(codeStr.trim());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "MFA code must be numeric"));
        }

        boolean verified = totpUtil.verifyCode(user.getMfaSecret(), code);
        if (!verified) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid verification code"));
        }

        user.setMfaEnabled(true);
        userRepository.save(user);
        log.info("SECURITY: MFA enabled for user: {}", phone);
        return ResponseEntity.ok(Map.of("message", "MFA enabled successfully"));
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<?> disableMfa(
            @RequestHeader("Authorization") String header,
            @RequestBody Map<String, String> body) {
        String token = header.substring(7);
        String phone = jwtUtil.getPhone(token);
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String codeStr = body.get("code");
        if (codeStr == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MFA code is required"));
        }

        int code;
        try {
            code = Integer.parseInt(codeStr.trim());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "MFA code must be numeric"));
        }

        boolean verified = totpUtil.verifyCode(user.getMfaSecret(), code);
        if (!verified) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid verification code"));
        }

        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        userRepository.save(user);
        log.info("SECURITY: MFA disabled for user: {}", phone);
        return ResponseEntity.ok(Map.of("message", "MFA disabled successfully"));
    }

    @PostMapping("/login/verify-mfa")
    public ResponseEntity<?> verifyMfaLogin(@RequestBody Map<String, String> body) {
        String mfaToken = body.get("mfaToken");
        String codeStr = body.get("code");

        if (mfaToken == null || codeStr == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "mfaToken and code are required"));
        }

        if (!jwtUtil.isValid(mfaToken) || !jwtUtil.isMfaToken(mfaToken)) {
            return ResponseEntity.status(401).body(Map.of("error", "MFA session expired or invalid"));
        }

        String phone = jwtUtil.getPhone(mfaToken);
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("User not found"));

        int code;
        try {
            code = Integer.parseInt(codeStr.trim());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "MFA code must be numeric"));
        }

        boolean verified = totpUtil.verifyCode(user.getMfaSecret(), code);
        if (!verified) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid verification code"));
        }

        String token = jwtUtil.generateToken(
                user.getPhone(),
                user.getRole().name(),
                user.getId().toString()
        );

        log.info("SECURITY: Successful MFA login verification for: {}", phone);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "role", user.getRole(),
                "name", user.getName(),
                "userId", user.getId(),
                "mustChangePassword", user.getMustChangePassword()
        ));
    }

    // ── Inner DTOs ───────────────────────────────────────────────

    @Data
    static class LoginRequest {
        private String phone;
        // max=72 prevents BCrypt Long Password DoS attack
        @Size(max = 72, message = "Password too long")
        private String password;
    }

    @Data
    static class ChangePasswordRequest {
        @Size(max = 72) private String currentPassword;
        @NotBlank(message = "New password cannot be blank")
        @Size(min = 8, max = 72, message = "New password must be between 8 and 72 characters")
        @jakarta.validation.constraints.Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,72}$",
                message = "New password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
        )
        private String newPassword;
    }
}