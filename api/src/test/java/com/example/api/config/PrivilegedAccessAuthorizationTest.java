package com.example.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrivilegedAccessAuthorizationTest {

    @Test
    void roleWithStrongAuth_allowsVerifiedPublisherWithMfa() {
        var access = PrivilegedAccessAuthorization.roleWithStrongAuth("PUBLISHER");

        var decision = access.check(
                () -> jwtAuthentication("ROLE_PUBLISHER", Map.of("email_verified", true, "acr", "1")),
                new RequestAuthorizationContext(new MockHttpServletRequest())
        );

        assertThat(decision).isNotNull();
        assertThat(decision.isGranted()).isTrue();
    }

    @Test
    void roleWithStrongAuth_rejectsPreviewRoleWithoutMfa() {
        var access = PrivilegedAccessAuthorization.roleWithStrongAuth("MANAGER");

        var decision = access.check(
                () -> jwtAuthentication("ROLE_MANAGER", Map.of("email_verified", true, "acr", "0")),
                new RequestAuthorizationContext(new MockHttpServletRequest())
        );

        assertThat(decision).isNotNull();
        assertThat(decision.isGranted()).isFalse();
    }

    @Test
    void roleWithStrongAuth_rejectsUnverifiedEmail() {
        var access = PrivilegedAccessAuthorization.roleWithStrongAuth("ADMIN");

        var decision = access.check(
                () -> jwtAuthentication("ROLE_ADMIN", Map.of("email_verified", false, "amr", List.of("otp"))),
                new RequestAuthorizationContext(new MockHttpServletRequest())
        );

        assertThat(decision).isNotNull();
        assertThat(decision.isGranted()).isFalse();
    }

    private JwtAuthenticationToken jwtAuthentication(String authority, Map<String, Object> claims) {
        Jwt jwt = new Jwt(
                "token",
                Instant.parse("2026-05-11T00:00:00Z"),
                Instant.parse("2026-05-11T01:00:00Z"),
                Map.of("alg", "none"),
                claims
        );
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority)));
    }
}
