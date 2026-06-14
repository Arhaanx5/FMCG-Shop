package com.shop.auth;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

public class TotpUtilTest {

    private final TotpUtil totpUtil = new TotpUtil();

    @Test
    public void testGenerateSecretKey() {
        String secret = totpUtil.generateSecretKey();
        assertNotNull(secret);
        assertEquals(16, secret.length());
        // Must contain only characters from standard Base32 alphabet
        assertTrue(secret.matches("^[A-Z2-7]+$"));
    }

    @Test
    public void testGetQrCodeUrl() {
        String secret = "ABCDEF2345677890";
        String phone = "9450821033";
        String url = totpUtil.getQrCodeUrl(secret, phone);
        assertNotNull(url);
        assertTrue(url.contains("https://chart.googleapis.com/chart"));
        assertTrue(url.contains("FMCG-Shop%3A9450821033"));
        assertTrue(url.contains("secret%3DABCDEF2345677890"));
    }

    @Test
    public void testVerifyCodeWithValidAndInvalidCodes() throws Exception {
        String secret = totpUtil.generateSecretKey();
        
        // Use reflection to call the private static method getCode(byte[] secret, long timeIndex)
        Method getCodeMethod = TotpUtil.class.getDeclaredMethod("getCode", byte[].class, long.class);
        getCodeMethod.setAccessible(true);
        
        Method decodeBase32Method = TotpUtil.class.getDeclaredMethod("decodeBase32", String.class);
        decodeBase32Method.setAccessible(true);
        
        byte[] secretBytes = (byte[]) decodeBase32Method.invoke(null, secret);
        long currentTimeIndex = System.currentTimeMillis() / 1000L / 30L;
        
        int correctCode = (int) getCodeMethod.invoke(null, secretBytes, currentTimeIndex);
        
        // Verify code
        assertTrue(totpUtil.verifyCode(secret, correctCode));
        
        // Verify with clock skew tolerance (-1 time step)
        int skewMinusCode = (int) getCodeMethod.invoke(null, secretBytes, currentTimeIndex - 1);
        assertTrue(totpUtil.verifyCode(secret, skewMinusCode));

        // Verify with clock skew tolerance (+1 time step)
        int skewPlusCode = (int) getCodeMethod.invoke(null, secretBytes, currentTimeIndex + 1);
        assertTrue(totpUtil.verifyCode(secret, skewPlusCode));

        // Verify with invalid code
        int invalidCode = (correctCode + 111111) % 1000000;
        assertFalse(totpUtil.verifyCode(secret, invalidCode));
    }

    @Test
    public void testVerifyCodeWithInvalidSecretOrEmpty() {
        assertFalse(totpUtil.verifyCode("", 123456));
        assertFalse(totpUtil.verifyCode(null, 123456));
    }
}
