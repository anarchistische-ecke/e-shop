package com.example.api.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

@Service
public class CmsPreviewTokenService {

    private static final Set<String> ALLOWED_COLLECTIONS = Set.of("page", "campaign", "banner");
    private final DirectusContentProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public CmsPreviewTokenService(
            DirectusContentProperties properties,
            ObjectMapper objectMapper
    ) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    CmsPreviewTokenService(
            DirectusContentProperties properties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public PreviewClaims verify(String token) {
        if (!StringUtils.hasText(token)) {
            throw new CmsPreviewUnauthorizedException("Preview token is required");
        }
        String secret = properties.getPreviewSecret();
        if (!StringUtils.hasText(secret) || secret.length() < 32) {
            throw new CmsPreviewUnauthorizedException("CMS preview is not configured");
        }
        String[] parts = token.trim().split("\\.", -1);
        if (parts.length != 2) {
            throw new CmsPreviewUnauthorizedException("Malformed preview token");
        }
        byte[] expected = hmac(parts[0], secret);
        byte[] provided;
        try {
            provided = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException error) {
            throw new CmsPreviewUnauthorizedException("Malformed preview signature");
        }
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new CmsPreviewUnauthorizedException("Invalid preview signature");
        }
        try {
            PreviewClaims claims = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[0]),
                    PreviewClaims.class
            );
            long now = Instant.now(clock).getEpochSecond();
            if (claims.v() != 1 || claims.exp() <= now || claims.iat() > now + 30) {
                throw new CmsPreviewUnauthorizedException("Preview token is expired");
            }
            if (
                    !ALLOWED_COLLECTIONS.contains(claims.collection())
                    || !StringUtils.hasText(claims.id())
            ) {
                throw new CmsPreviewUnauthorizedException("Unsupported preview target");
            }
            return claims;
        } catch (CmsPreviewUnauthorizedException error) {
            throw error;
        } catch (Exception error) {
            throw new CmsPreviewUnauthorizedException("Malformed preview payload");
        }
    }

    private byte[] hmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to verify CMS preview token", error);
        }
    }

    public record PreviewClaims(
            int v,
            String collection,
            String id,
            String version,
            long iat,
            long exp,
            String nonce,
            String actor,
            String returnPath
    ) {
    }
}
