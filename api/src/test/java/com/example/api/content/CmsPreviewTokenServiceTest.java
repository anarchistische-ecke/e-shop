package com.example.api.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CmsPreviewTokenServiceTest {

    private static final String SECRET = "preview-secret-with-at-least-thirty-two-characters";
    private static final Instant NOW = Instant.parse("2026-08-03T10:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validatesSignedShortLivedClaims() throws Exception {
        CmsPreviewTokenService service = service();
        String token = token(Map.of(
                "v", 1,
                "collection", "page",
                "id", "12",
                "version", "release-candidate",
                "iat", NOW.getEpochSecond(),
                "exp", NOW.plusSeconds(300).getEpochSecond(),
                "nonce", "nonce",
                "actor", "editor",
                "returnPath", "/"
        ));

        CmsPreviewTokenService.PreviewClaims claims = service.verify(token);

        assertThat(claims.collection()).isEqualTo("page");
        assertThat(claims.id()).isEqualTo("12");
        assertThat(claims.version()).isEqualTo("release-candidate");
    }

    @Test
    void rejectsExpiredAndTamperedTokens() throws Exception {
        CmsPreviewTokenService service = service();
        String expired = token(Map.of(
                "v", 1,
                "collection", "campaign",
                "id", "7",
                "version", "",
                "iat", NOW.minusSeconds(600).getEpochSecond(),
                "exp", NOW.getEpochSecond(),
                "nonce", "nonce",
                "actor", "editor",
                "returnPath", "/promo/sale"
        ));

        assertThatThrownBy(() -> service.verify(expired))
                .isInstanceOf(CmsPreviewUnauthorizedException.class)
                .hasMessageContaining("expired");
        assertThatThrownBy(() -> service.verify(expired + "x"))
                .isInstanceOf(CmsPreviewUnauthorizedException.class)
                .hasMessageContaining("signature");
    }

    private CmsPreviewTokenService service() {
        DirectusContentProperties properties = new DirectusContentProperties();
        properties.setPreviewSecret(SECRET);
        return new CmsPreviewTokenService(
                properties,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private String token(Map<String, Object> claims) throws Exception {
        String payload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(claims));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        return payload + "." + signature;
    }
}
