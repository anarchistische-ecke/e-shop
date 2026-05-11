package com.example.api.admincms;

import com.example.api.catalog.DirectusBridgeSecurity;
import com.example.api.catalog.DirectusStorefrontOpsRolePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectusAdminRoleGuardTest {

    private DirectusAdminRoleGuard guard;

    @BeforeEach
    void setUp() {
        DirectusStorefrontOpsRolePolicy rolePolicy = new DirectusStorefrontOpsRolePolicy();
        ReflectionTestUtils.setField(rolePolicy, "adminRoles", "admin,admin-role");
        ReflectionTestUtils.setField(rolePolicy, "managerRoles", "manager-role");
        ReflectionTestUtils.setField(rolePolicy, "pickerRoles", "picker-role");
        ReflectionTestUtils.setField(rolePolicy, "contentManagerRoles", "content-role,legacy-catalogue-role");
        guard = new DirectusAdminRoleGuard(rolePolicy);
    }

    @Test
    void requireOrders_allowsManagerAndPickerButRejectsContentOnlyRole() {
        assertThatCode(() -> guard.requireOrders(principal("manager-role"))).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireOrders(principal("picker-role"))).doesNotThrowAnyException();

        assertThatThrownBy(() -> guard.requireOrders(principal("content-role")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireContent_allowsContentManagerAndAdminButRejectsManager() {
        assertThatCode(() -> guard.requireContent(principal("content-role"))).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireContent(principal("admin"))).doesNotThrowAnyException();

        assertThatThrownBy(() -> guard.requireContent(principal("manager-role")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireAnalytics_allowsManagerButRejectsContentOnlyRole() {
        assertThatCode(() -> guard.requireAnalytics(principal("admin-role"))).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireAnalytics(principal("manager-role"))).doesNotThrowAnyException();

        assertThatThrownBy(() -> guard.requireAnalytics(principal("content-role")))
                .isInstanceOf(AccessDeniedException.class);
    }

    private DirectusBridgeSecurity.DirectusBridgePrincipal principal(String role) {
        return new DirectusBridgeSecurity.DirectusBridgePrincipal("user-id", "user@example.test", "external-id", role, role);
    }
}
