package com.shop;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class VerifyPassword {
    @Test
    public void testPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        System.out.println("=========================================");
        System.out.println("UAT Match: " + encoder.matches("admin123", "$2a$10$fFY6Gqnwtt1xsMtqfXDLUeN3FNrfI.GAw9wkKmrJWBUkhAx5c0vKC"));
        System.out.println("PROD Match: " + encoder.matches("admin123", "$2a$10$8T3iLj6vHPyGHVleXzxE9.L4.BSstBZf16ievvJzeNiAAL.uYaGLq"));
        System.out.println("=========================================");
    }
}
