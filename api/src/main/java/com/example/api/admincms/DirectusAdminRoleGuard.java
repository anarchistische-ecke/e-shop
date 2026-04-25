package com.example.api.admincms;

import com.example.api.catalog.DirectusBridgeSecurity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class DirectusAdminRoleGuard {

    @Value("${app.directus.roles.admin:admin,4c4cc8d0-9b7f-4d56-84d2-1d64f5f10001}")
    private String adminRoles;

    @Value("${app.directus.roles.manager:manager,4c4cc8d0-9b7f-4d56-84d2-1d64f5f10006}")
    private String managerRoles;

    @Value("${app.directus.roles.picker:picker,4c4cc8d0-9b7f-4d56-84d2-1d64f5f10007}")
    private String pickerRoles;

    @Value("${app.directus.roles.content-manager:content_manager,content-manager,4c4cc8d0-9b7f-4d56-84d2-1d64f5f10008,4c4cc8d0-9b7f-4d56-84d2-1d64f5f10002,4c4cc8d0-9b7f-4d56-84d2-1d64f5f10004,4c4cc8d0-9b7f-4d56-84d2-1d64f5f10005}")
    private String contentManagerRoles;

    public void requireOrders(DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        requireAny(principal, "orders", adminTokens(), managerTokens(), pickerTokens());
    }

    public void requireRma(DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        requireAny(principal, "RMA requests", adminTokens(), managerTokens());
    }

    public void requireContent(DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        requireAny(principal, "content operations", adminTokens(), contentManagerTokens());
    }

    public void requireTax(DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        requireAny(principal, "tax settings", adminTokens());
    }

    public void requireAnalytics(DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        requireAny(principal, "analytics", adminTokens(), managerTokens());
    }

    public void requirePromotionsRead(DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        requireAny(principal, "active promotions", adminTokens(), managerTokens(), pickerTokens(), contentManagerTokens());
    }

    public boolean isAdmin(DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        return hasAny(principal, adminTokens());
    }

    public boolean isManager(DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        return hasAny(principal, managerTokens());
    }

    public boolean isPicker(DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        return hasAny(principal, pickerTokens());
    }

    @SafeVarargs
    private void requireAny(DirectusBridgeSecurity.DirectusBridgePrincipal principal,
                            String scope,
                            Collection<String>... allowedGroups) {
        for (Collection<String> allowedGroup : allowedGroups) {
            if (hasAny(principal, allowedGroup)) {
                return;
            }
        }
        throw new AccessDeniedException("Directus role is not allowed to access " + scope);
    }

    private boolean hasAny(DirectusBridgeSecurity.DirectusBridgePrincipal principal, Collection<String> allowedTokens) {
        Set<String> actual = principalTokens(principal);
        for (String allowed : allowedTokens) {
            if (actual.contains(allowed)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> principalTokens(DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        Set<String> tokens = new LinkedHashSet<>();
        addTokens(tokens, principal.primaryRole());
        addTokens(tokens, principal.roles());
        return tokens;
    }

    private Set<String> adminTokens() {
        return roleTokens(adminRoles);
    }

    private Set<String> managerTokens() {
        return roleTokens(managerRoles);
    }

    private Set<String> pickerTokens() {
        return roleTokens(pickerRoles);
    }

    private Set<String> contentManagerTokens() {
        return roleTokens(contentManagerRoles);
    }

    private Set<String> roleTokens(String value) {
        Set<String> tokens = new LinkedHashSet<>();
        addTokens(tokens, value);
        return tokens;
    }

    private void addTokens(Set<String> tokens, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        for (String token : value.split(",")) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                tokens.add(normalized);
            }
        }
    }
}
