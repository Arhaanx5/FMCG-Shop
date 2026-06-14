package com.shop.auth;

import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import com.shop.modules.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthControllerMfaTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private TotpUtil totpUtil;

    @InjectMocks
    private AuthController authController;

    @Test
    public void testLoginMfaRequiredForAdmin() {
        // Arrange
        AuthController.LoginRequest req = new AuthController.LoginRequest();
        req.setPhone("9450821033");
        req.setPassword("admin123");

        User user = User.builder()
                .id(UUID.randomUUID())
                .name("Arhaan")
                .phone("9450821033")
                .passwordHash("hashed")
                .role(UserRole.ADMIN)
                .active(true)
                .mustChangePassword(false)
                .mfaEnabled(true)
                .mfaSecret("SECRET32")
                .build();

        when(userRepository.findByPhone("9450821033")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("admin123", "hashed")).thenReturn(true);
        when(jwtUtil.generateMfaToken("9450821033", user.getId().toString())).thenReturn("temp-mfa-token");

        // Act
        ResponseEntity<?> response = authController.login(req);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals(true, body.get("mfaRequired"));
        assertEquals("temp-mfa-token", body.get("mfaToken"));
        assertEquals("9450821033", body.get("phone"));
    }

    @Test
    public void testLoginMfaBypassedForSalesmanEvenIfEnabled() {
        // Arrange
        AuthController.LoginRequest req = new AuthController.LoginRequest();
        req.setPhone("9450821033");
        req.setPassword("sales123");

        User user = User.builder()
                .id(UUID.randomUUID())
                .name("Arhaan")
                .phone("9450821033")
                .passwordHash("hashed")
                .role(UserRole.SALESMAN)
                .active(true)
                .mustChangePassword(false)
                .mfaEnabled(true)
                .mfaSecret("SECRET32")
                .build();

        when(userRepository.findByPhone("9450821033")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("sales123", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken("9450821033", "SALESMAN", user.getId().toString())).thenReturn("real-jwt-token");

        // Act
        ResponseEntity<?> response = authController.login(req);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertNull(body.get("mfaRequired"));
        assertEquals("real-jwt-token", body.get("token"));
        assertEquals(UserRole.SALESMAN, body.get("role"));
    }

    @Test
    public void testSetupMfaSuccess() {
        // Arrange
        String header = "Bearer real-jwt-token";
        when(jwtUtil.getPhone("real-jwt-token")).thenReturn("9450821033");

        User user = User.builder()
                .id(UUID.randomUUID())
                .phone("9450821033")
                .mfaEnabled(false)
                .build();

        when(userRepository.findByPhone("9450821033")).thenReturn(Optional.of(user));
        when(totpUtil.generateSecretKey()).thenReturn("NEWSECRET32");
        when(totpUtil.getQrCodeUrl("NEWSECRET32", "9450821033")).thenReturn("http://qr-code-url");

        // Act
        ResponseEntity<?> response = authController.setupMfa(header);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals("NEWSECRET32", body.get("secret"));
        assertEquals("http://qr-code-url", body.get("qrCodeUrl"));
        assertEquals("NEWSECRET32", user.getMfaSecret());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    public void testEnableMfaSuccess() {
        // Arrange
        String header = "Bearer real-jwt-token";
        when(jwtUtil.getPhone("real-jwt-token")).thenReturn("9450821033");

        User user = User.builder()
                .id(UUID.randomUUID())
                .phone("9450821033")
                .mfaSecret("SECRET32")
                .mfaEnabled(false)
                .build();

        when(userRepository.findByPhone("9450821033")).thenReturn(Optional.of(user));
        when(totpUtil.verifyCode("SECRET32", 123456)).thenReturn(true);

        Map<String, String> bodyReq = Map.of("code", "123456");

        // Act
        ResponseEntity<?> response = authController.enableMfa(header, bodyReq);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(user.getMfaEnabled());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    public void testEnableMfaFailureInvalidCode() {
        // Arrange
        String header = "Bearer real-jwt-token";
        when(jwtUtil.getPhone("real-jwt-token")).thenReturn("9450821033");

        User user = User.builder()
                .id(UUID.randomUUID())
                .phone("9450821033")
                .mfaSecret("SECRET32")
                .mfaEnabled(false)
                .build();

        when(userRepository.findByPhone("9450821033")).thenReturn(Optional.of(user));
        when(totpUtil.verifyCode("SECRET32", 111111)).thenReturn(false);

        Map<String, String> bodyReq = Map.of("code", "111111");

        // Act
        ResponseEntity<?> response = authController.enableMfa(header, bodyReq);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(user.getMfaEnabled());
        verify(userRepository, never()).save(any());
    }

    @Test
    public void testVerifyMfaLoginSuccess() {
        // Arrange
        Map<String, String> bodyReq = Map.of("mfaToken", "temp-mfa-token", "code", "123456");

        User user = User.builder()
                .id(UUID.randomUUID())
                .name("Arhaan")
                .phone("9450821033")
                .role(UserRole.ADMIN)
                .mfaSecret("SECRET32")
                .mfaEnabled(true)
                .mustChangePassword(false)
                .build();

        when(jwtUtil.isValid("temp-mfa-token")).thenReturn(true);
        when(jwtUtil.isMfaToken("temp-mfa-token")).thenReturn(true);
        when(jwtUtil.getPhone("temp-mfa-token")).thenReturn("9450821033");
        when(userRepository.findByPhone("9450821033")).thenReturn(Optional.of(user));
        when(totpUtil.verifyCode("SECRET32", 123456)).thenReturn(true);
        when(jwtUtil.generateToken("9450821033", "ADMIN", user.getId().toString())).thenReturn("final-real-jwt");

        // Act
        ResponseEntity<?> response = authController.verifyMfaLogin(bodyReq);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals("final-real-jwt", body.get("token"));
        assertEquals(UserRole.ADMIN, body.get("role"));
    }
}
