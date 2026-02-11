package com.example.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.security.trusted-resource-role-clients:}")
    private String trustedResourceRoleClients;

    @Value("${app.security.require-strong-auth-for-privileged:true}")
    private boolean requireStrongAuthForPrivileged;

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuer
    ) {
        if (!StringUtils.hasText(issuer)) {
            throw new IllegalStateException("Keycloak issuer-uri is required");
        }
        return JwtDecoders.fromIssuerLocation(issuer);
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                new KeycloakJwtAuthoritiesConverter(trustedResourceRoleClients)
        );
        return converter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        AuthorizationManager<RequestAuthorizationContext> adminAccess =
                privilegedAccess("ADMIN");
        AuthorizationManager<RequestAuthorizationContext> managerAccess =
                privilegedAccess("MANAGER");

        http.csrf().disable();
        // Allow CORS from configured origins (CORS config is handled separately)
        http.cors();
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        http.authorizeHttpRequests(auth -> auth
                // Health/diagnostics endpoints
                .requestMatchers("/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/customers/me").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.PUT, "/customers/me").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.PUT, "/customers/me/subscription").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET, "/orders/me").hasRole("CUSTOMER")
                // Allow cart operations and checkout for guests
                .requestMatchers("/carts/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/orders").permitAll()
                .requestMatchers(HttpMethod.POST, "/orders/checkout").permitAll()
                .requestMatchers(HttpMethod.POST, "/deliveries/yandex/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/orders/public/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/orders/public/**").permitAll()
                .requestMatchers(HttpMethod.PUT, "/orders/**").access(adminAccess)
                .requestMatchers(HttpMethod.POST, "/payments/yookassa/webhook").permitAll()
                .requestMatchers(HttpMethod.POST, "/payments/yookassa/refund").access(adminAccess)
                .requestMatchers(HttpMethod.POST, "/payments/yookassa/cancel").access(adminAccess)
                // Allow anyone to view products, categories and brands via GET
                .requestMatchers(HttpMethod.GET, "/products/**", "/categories/**", "/brands/**").permitAll()
                // Inventory adjustments require admin privileges
                .requestMatchers(HttpMethod.POST, "/inventory/**").access(adminAccess)
                .requestMatchers(HttpMethod.POST, "/orders/admin-link").access(adminAccess)
                .requestMatchers(HttpMethod.POST, "/orders/manager-link").access(managerAccess)
                .requestMatchers("/managers/**").access(managerAccess)
                // Require admin for creating, updating or deleting products, categories and brands
                .requestMatchers(HttpMethod.POST, "/products/**", "/categories/**", "/brands/**").access(adminAccess)
                .requestMatchers(HttpMethod.PUT, "/products/**", "/categories/**", "/brands/**").access(adminAccess)
                .requestMatchers(HttpMethod.DELETE, "/products/**", "/categories/**", "/brands/**").access(adminAccess)
                // Admin-only endpoints
                .requestMatchers("/admin/**").access(adminAccess)
                // Only admins can view orders, customers and shipments
                .requestMatchers(HttpMethod.GET, "/orders", "/orders/*", "/customers/**", "/shipments/**").access(adminAccess)
                .requestMatchers(HttpMethod.POST, "/orders/*/delivery/**").access(adminAccess)
                // All other API calls require authentication
                .anyRequest().authenticated()
        );
        http.oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
        );
        http.exceptionHandling().authenticationEntryPoint((req, res, ex) -> res.sendError(401, "Unauthorized"));
        return http.build();
    }

    private AuthorizationManager<RequestAuthorizationContext> privilegedAccess(String role) {
        if (!requireStrongAuthForPrivileged) {
            return AuthorityAuthorizationManager.hasRole(role);
        }
        return PrivilegedAccessAuthorization.roleWithStrongAuth(role);
    }
}
