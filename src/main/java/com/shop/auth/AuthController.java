package com.shop.auth;

import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        User user = userRepository.findByPhone(req.getPhone())
                .orElse(null);

        if (user == null || !user.getActive()) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "Invalid credentials"));
        }

        if (!passwordEncoder.matches(req.getPassword(),
                user.getPasswordHash())) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "Invalid credentials"));
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
                "mustChangePassword", user.getMustChangePassword()
        ));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestHeader("Authorization") String header,
            @RequestBody ChangePasswordRequest req) {

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
        return ResponseEntity.ok(Map.of("message", "Password changed"));
    }

    @Data
    static class LoginRequest {
        private String phone;
        private String password;
    }

    @Data
    static class ChangePasswordRequest {
        private String currentPassword;
        private String newPassword;
    }
}