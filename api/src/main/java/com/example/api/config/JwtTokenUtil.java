package com.example.api.config;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class JwtTokenUtil {
    /**
     * Generate a signed JWT token with the given subject (e.g. username or email), role, and expiration.
     */
    public static String generateToken(String subject, String role, long expirationMillis, String secret) {
        // JWT header and payload
        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        long expSeconds = (System.currentTimeMillis() + expirationMillis) / 1000;
        String payloadJson = String.format("{\"sub\":\"%s\",\"role\":\"%s\",\"exp\":%d}", subject, role, expSeconds);
        // Base64URL encode header and payload
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        // Sign header.payload with HMAC SHA256
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signatureBytes = mac.doFinal((header + "." + payload).getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
            return header + "." + payload + "." + signature;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("JWT Token generation failed", e);
        }
    }
}

