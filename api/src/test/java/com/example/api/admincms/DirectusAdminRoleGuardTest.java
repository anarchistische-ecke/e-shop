package com.example.api.admincms;

import com.example.api.catalog.DirectusBridgeSecurity;
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
        guard = new DirectusAdminRoleGuard();
        ReflectionTestUtils.setField(guard, "adminRoles", "admin,admin-role");
        ReflectionTestUtils.setField(guard, "managerRoles", "manager-role");
        ReflectionTestUtils.setField(guard, "pickerRoles", "picker-role");
        ReflectionTestUtils.setField(guard, "contentManagerRoles", "content-role,legacy-catalogue-role");
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
    void requireAnalytics_isAdminOnly() {
        assertThatCode(() -> guard.requireAnalytics(principal("admin-role"))).doesNotThrowAnyException();

        assertThatThrownBy(() -> guard.requireAnalytics(principal("manager-role")))
                .isInstanceOf(AccessDeniedException.class);
    }

    private DirectusBridgeSecurity.DirectusBridgePrincipal principal(String role) {
        return new DirectusBridgeSecurity.DirectusBridgePrincipal("user-id", "user@example.test", role, role);
    }
}
