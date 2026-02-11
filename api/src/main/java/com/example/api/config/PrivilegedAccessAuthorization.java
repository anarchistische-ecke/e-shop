package com.example.api.config;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

final class PrivilegedAccessAuthorization {

    private static final Set<String> MFA_AMR_VALUES = Set.of(
            "mfa",
            "otp",
            "totp",
            "webauthn",
            "hwk",
            "sms"
    );

    private PrivilegedAccessAuthorization() {
    }

    static AuthorizationManager<RequestAuthorizationContext> roleWithStrongAuth(String role) {
        final String expectedAuthority = normalizeRole(role);
        return (Supplier<Authentication> authentication, RequestAuthorizationContext context) -> {
            Authentication auth = authentication.get();
            if (!hasAuthority(auth, expectedAuthority)) {
                return new AuthorizationDecision(false);
            }
            if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
                return new AuthorizationDecision(false);
            }
            Map<String, Object> claims = jwtAuth.getToken().getClaims();
            return new AuthorizationDecision(isEmailVerified(claims) && hasMfa(claims));
        };
    }

    private static boolean hasAuthority(Authentication auth, String expectedAuthority) {
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        if (authorities == null) {
            return false;
        }
        return authorities.stream().anyMatch(a -> expectedAuthority.equals(a.getAuthority()));
    }

    private static String normalizeRole(String role) {
        if (role == null) {
            return "";
        }
        if (role.startsWith("ROLE_")) {
            return role;
        }
        return "ROLE_" + role.toUpperCase(Locale.ROOT);
    }

    private static boolean isEmailVerified(Map<String, Object> claims) {
        return claims != null && Boolean.TRUE.equals(claims.get("email_verified"));
    }

    private static boolean hasMfa(Map<String, Object> claims) {
        if (claims == null) {
            return false;
        }
        Object acr = claims.get("acr");
        if (acr != null) {
            String acrValue = acr.toString().trim();
            if (!acrValue.isEmpty() && !"0".equals(acrValue)) {
                return true;
            }
        }
        Object amr = claims.get("amr");
        if (!(amr instanceof Collection<?> methods)) {
            return false;
        }
        for (Object method : methods) {
            if (method == null) {
                continue;
            }
            String normalized = method.toString().trim().toLowerCase(Locale.ROOT);
            if (MFA_AMR_VALUES.contains(normalized)) {
                return true;
            }
        }
        return false;
    }
}
