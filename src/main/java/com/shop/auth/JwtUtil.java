package com.shop.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private long expiration;

    private Key getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(String phone, String role, String userId) {
        return Jwts.builder()
                .setSubject(phone)
                .claim("role", role)
                .claim("userId", userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateMfaToken(String phone, String userId) {
        return Jwts.builder()
                .setSubject(phone)
                .claim("userId", userId)
                .claim("mfaRequired", true)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 5 * 60 * 1000L)) // 5 minutes
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String getPhone(String token) {
        return getClaims(token).getSubject();
    }

    public String getRole(String token) {
        return (String) getClaims(token).get("role");
    }

    public String getUserId(String token) {
        return (String) getClaims(token).get("userId");
    }

    public boolean isMfaToken(String token) {
        try {
            Boolean mfa = (Boolean) getClaims(token).get("mfaRequired");
            return mfa != null && mfa;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isValid(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}