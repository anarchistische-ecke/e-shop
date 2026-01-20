package com.example.api.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class KeycloakJwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<String> roles = new HashSet<>();
        extractRealmRoles(roles, jwt.getClaim("realm_access"));
        extractResourceRoles(roles, jwt.getClaim("resource_access"));
        extractCustomRoles(roles, jwt);

        return roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(this::normalizeRole)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    private void extractCustomRoles(Set<String> roles, Jwt jwt) {
        if (jwt == null) {
            return;
        }
        Object roleClaim = jwt.getClaim("role");
        addRole(roles, roleClaim);
        Object rolesClaim = jwt.getClaim("roles");
        if (rolesClaim instanceof Collection<?> list) {
            for (Object role : list) {
                addRole(roles, role);
            }
        }
    }

    private void extractRealmRoles(Set<String> roles, Map<String, Object> realmAccess) {
        if (realmAccess == null) {
            return;
        }
        Object realmRoles = realmAccess.get("roles");
        if (realmRoles instanceof Collection<?> roleList) {
            for (Object role : roleList) {
                addRole(roles, role);
            }
        }
    }

    private void extractResourceRoles(Set<String> roles, Map<String, Object> resourceAccess) {
        if (resourceAccess == null) {
            return;
        }
        for (Object value : resourceAccess.values()) {
            if (value instanceof Map<?, ?> clientAccess) {
                Object clientRoles = clientAccess.get("roles");
                if (clientRoles instanceof Collection<?> roleList) {
                    for (Object role : roleList) {
                        addRole(roles, role);
                    }
                }
            }
        }
    }

    private void addRole(Set<String> roles, Object role) {
        if (role == null) {
            return;
        }
        String roleName = role.toString();
        if (!roleName.isBlank()) {
            roles.add(roleName);
        }
    }

    private String normalizeRole(String role) {
        if (role.startsWith("ROLE_")) {
            return role;
        }
        return "ROLE_" + role.toUpperCase();
    }
}
