package com.example.api.admincms;

import com.example.api.catalog.DirectusBridgeSecurity;
import com.example.api.catalog.DirectusStorefrontOpsRolePolicy;
import org.springframework.stereotype.Component;

@Component
public class DirectusAdminRoleGuard {

    private final DirectusStorefrontOpsRolePolicy rolePolicy;

    public DirectusAdminRoleGuard(DirectusStorefrontOpsRolePolicy rolePolicy) {
        this.rolePolicy = rolePolicy;
    }

    public void requireOrders(DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        rolePolicy.requireOrders(principal);
    }

    public void requireRma(DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        rolePolicy.requireRma(principal);
    }

    public void requireContent(DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        rolePolicy.requireContent(principal);
    }

    public void requireTax(DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        rolePolicy.requireTax(principal);
    }

    public void requireAdmin(DirectusBridgeSecurity.DirectusBridgePrincipal principal, String scope) {
        rolePolicy.requireAdmin(principal, scope);
    }

    public void requireAnalytics(DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        rolePolicy.requireAnalytics(principal);
    }

    public void requirePromotionsRead(DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        rolePolicy.requirePromotionsRead(principal);
    }

    public boolean isAdmin(DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        return rolePolicy.isAdmin(principal);
    }

    public boolean isManager(DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        return rolePolicy.isManager(principal);
    }

    public boolean isPicker(DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        return rolePolicy.isPicker(principal);
    }
}
