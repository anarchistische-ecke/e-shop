package com.example.api.catalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class DirectusBridgeSecurity {

    public static final String BRIDGE_TOKEN_HEADER = "X-Directus-Bridge-Token";
    public static final String USER_ID_HEADER = "X-Directus-User-Id";
    public static final String USER_EMAIL_HEADER = "X-Directus-User-Email";
    public static final String USER_EXTERNAL_ID_HEADER = "X-Directus-User-External-Id";
    public static final String USER_ROLE_HEADER = "X-Directus-User-Role";
    public static final String USER_ROLES_HEADER = "X-Directus-User-Roles";

    @Value("${app.security.directus-bridge-token:}")
    private String bridgeToken;

    public void authorize(HttpServletRequest request) {
        if (!StringUtils.hasText(bridgeToken)) {
            throw new DirectusBridgeUnauthorizedException("Directus bridge token is not configured");
        }

        String provided = request.getHeader(BRIDGE_TOKEN_HEADER);
        if (!StringUtils.hasText(provided) || !bridgeToken.equals(provided.trim())) {
            throw new DirectusBridgeUnauthorizedException("Invalid Directus bridge token");
        }
    }

    public DirectusBridgePrincipal principal(HttpServletRequest request) {
        return new DirectusBridgePrincipal(
                normalize(request.getHeader(USER_ID_HEADER)),
                normalize(request.getHeader(USER_EMAIL_HEADER)),
                normalize(request.getHeader(USER_EXTERNAL_ID_HEADER)),
                normalize(request.getHeader(USER_ROLE_HEADER)),
                normalize(request.getHeader(USER_ROLES_HEADER))
        );
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record DirectusBridgePrincipal(
            String userId,
            String email,
            String externalId,
            String primaryRole,
            String roles
    ) {
        public String actor() {
            if (StringUtils.hasText(email)) {
                return email;
            }
            if (StringUtils.hasText(userId)) {
                return userId;
            }
            return "directus-bridge";
        }
    }
}
