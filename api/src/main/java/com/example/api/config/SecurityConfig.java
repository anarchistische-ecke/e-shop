package com.example.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuer,
            @Value("${jwt.secret:}") String secret
    ) {
        if (StringUtils.hasText(issuer)) {
            return JwtDecoders.fromIssuerLocation(issuer);
        }
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("JWT secret is required when issuer-uri is not configured");
        }
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakJwtAuthoritiesConverter());
        return converter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf().disable();
        // Allow CORS from configured origins (CORS config is handled separately)
        http.cors();
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        http.authorizeHttpRequests(auth -> auth
                // Health/diagnostics endpoints
                .requestMatchers("/health/**").permitAll()
                // Allow login and user registration without authentication
                .requestMatchers(HttpMethod.POST, "/auth/**", "/customers/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/customers/verify/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/customers/me").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.PUT, "/customers/me").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.PUT, "/customers/me/subscription").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET, "/orders/me").hasRole("CUSTOMER")
                // Allow cart operations and checkout for guests
                .requestMatchers("/carts/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/orders").permitAll()
                .requestMatchers(HttpMethod.POST, "/orders/checkout").permitAll()
                .requestMatchers(HttpMethod.GET, "/orders/public/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/orders/public/**").permitAll()
                .requestMatchers(HttpMethod.PUT, "/orders/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/payments/yookassa/webhook").permitAll()
                .requestMatchers(HttpMethod.POST, "/payments/yookassa/refund").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/payments/yookassa/cancel").hasRole("ADMIN")
                // Allow anyone to view products, categories and brands via GET
                .requestMatchers(HttpMethod.GET, "/products/**", "/categories/**", "/brands/**").permitAll()
                // Inventory adjustments require admin privileges
                .requestMatchers(HttpMethod.POST, "/inventory/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/orders/admin-link").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/orders/manager-link").hasRole("MANAGER")
                .requestMatchers("/managers/**").hasRole("MANAGER")
                // Require admin for creating, updating or deleting products, categories and brands
                .requestMatchers(HttpMethod.POST, "/products/**", "/categories/**", "/brands/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/products/**", "/categories/**", "/brands/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/products/**", "/categories/**", "/brands/**").hasRole("ADMIN")
                // Admin-only endpoints
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // Only admins can view orders, customers and shipments
                .requestMatchers(HttpMethod.GET, "/orders", "/orders/*", "/customers/**", "/shipments/**").hasRole("ADMIN")
                // All other API calls require authentication
                .anyRequest().authenticated()
        );
        http.oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
        );
        http.exceptionHandling().authenticationEntryPoint((req, res, ex) -> res.sendError(401, "Unauthorized"));
        return http.build();
    }
}
