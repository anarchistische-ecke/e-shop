package com.example.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class JwtTokenFilter extends OncePerRequestFilter {
    private final String jwtSecret;

    public JwtTokenFilter(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // No JWT provided, continue without authentication
            chain.doFilter(request, response);
            return;
        }
        String token = authHeader.substring(7);
        try {
            // Split token into parts
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new RuntimeException("Invalid JWT token format");
            }
            String headerB64 = parts[0];
            String payloadB64 = parts[1];
            String signatureB64 = parts[2];
            // Verify signature (HMAC SHA256)
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expectedSigBytes = mac.doFinal((headerB64 + "." + payloadB64).getBytes(StandardCharsets.UTF_8));
            String expectedSig = Base64.getUrlEncoder().withoutPadding().encodeToString(expectedSigBytes);
            if (!expectedSig.equals(signatureB64)) {
                throw new RuntimeException("Invalid JWT signature");
            }
            // Parse payload JSON
            String payloadJson = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
            JsonNode claims = new ObjectMapper().readTree(payloadJson);
            String subject = claims.has("sub") ? claims.get("sub").textValue() : null;
            String role = claims.has("role") ? claims.get("role").textValue() : null;
            long exp = claims.has("exp") ? claims.get("exp").asLong() : 0;
            if (subject == null || role == null) {
                throw new RuntimeException("Invalid JWT payload");
            }
            // Check expiration
            long nowSec = System.currentTimeMillis() / 1000;
            if (exp > 0 && nowSec > exp) {
                throw new RuntimeException("JWT token expired");
            }
            // Create Authentication and set in security context
            List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
            Authentication auth = new UsernamePasswordAuthenticationToken(subject, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception ex) {
            // Invalid token: respond with 401 and abort filter chain
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return;
        }
        // Token is valid – continue to next filter
        chain.doFilter(request, response);
    }
}
