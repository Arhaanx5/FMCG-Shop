package com.shop.auth;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;

@Component
public class TotpUtil {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    /**
     * Generate a random 16-character Base32 secret key.
     */
    public String generateSecretKey() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Get Google Charts QR Code URL for the TOTP setup.
     */
    public String getQrCodeUrl(String secret, String phone) {
        String label = "FMCG-Shop:" + phone;
        String issuer = "FMCG-Shop";
        String uri = String.format("otpauth://totp/%s?secret=%s&issuer=%s", label, secret, issuer);
        try {
            return "https://chart.googleapis.com/chart?chs=200x200&chld=M|0&cht=qr&chl=" 
                    + URLEncoder.encode(uri, "UTF-8");
        } catch (Exception e) {
            return uri;
        }
    }

    /**
     * Verify a 6-digit TOTP code against a secret key, with clock skew allowance.
     */
    public boolean verifyCode(String secretBase32, int code) {
        if (secretBase32 == null || secretBase32.isBlank()) {
            return false;
        }
        try {
            byte[] secretBytes = decodeBase32(secretBase32);
            long currentTimeIndex = System.currentTimeMillis() / 1000L / 30L;

            // Allow clock skew of 1 time-step (30 seconds) in either direction
            for (int i = -1; i <= 1; i++) {
                if (getCode(secretBytes, currentTimeIndex + i) == code) {
                    return true;
                }
            }
        } catch (Exception e) {
            // ignore or log
        }
        return false;
    }

    private static int getCode(byte[] secret, long timeIndex) throws Exception {
        byte[] data = new byte[8];
        long value = timeIndex;
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (value & 0xFF);
            value >>= 8;
        }

        SecretKeySpec signKey = new SecretKeySpec(secret, "HmacSHA1");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(signKey);
        byte[] hash = mac.doFinal(data);

        int offset = hash[hash.length - 1] & 0xF;

        long truncatedHash = 0;
        for (int i = 0; i < 4; ++i) {
            truncatedHash <<= 8;
            truncatedHash |= (hash[offset + i] & 0xFF);
        }

        truncatedHash &= 0x7FFFFFFF;
        truncatedHash %= 1000000;

        return (int) truncatedHash;
    }

    private static byte[] decodeBase32(String base32) {
        String normalized = base32.toUpperCase().replace("=", "");
        int numBytes = (normalized.length() * 5) / 8;
        byte[] result = new byte[numBytes];

        int buffer = 0;
        int bitsLeft = 0;
        int resultIndex = 0;

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            int val = ALPHABET.indexOf(c);
            if (val == -1) {
                throw new IllegalArgumentException("Invalid Base32 character: " + c);
            }

            buffer = (buffer << 5) | val;
            bitsLeft += 5;

            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                if (resultIndex < numBytes) {
                    result[resultIndex++] = (byte) ((buffer >> bitsLeft) & 0xFF);
                }
            }
        }
        return result;
    }
}
