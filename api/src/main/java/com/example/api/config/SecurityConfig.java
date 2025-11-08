package com.example.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf().disable();
        // Allow CORS from configured origins (CORS config is handled separately)
        http.cors();
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        http.authorizeHttpRequests(auth -> auth
                // Allow login and user registration without authentication
                .requestMatchers(HttpMethod.POST, "/auth/**", "/customers/register").permitAll()
                // Allow anyone to view products, categories and brands via GET
                .requestMatchers(HttpMethod.GET, "/products/**", "/categories/**", "/brands/**").permitAll()
                // Require admin for creating, updating or deleting products, categories and brands
                .requestMatchers(HttpMethod.POST, "/products/**", "/categories/**", "/brands/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/products/**", "/categories/**", "/brands/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/products/**", "/categories/**", "/brands/**").hasRole("ADMIN")
                // Only admins can view orders, customers and shipments
                .requestMatchers(HttpMethod.GET, "/orders", "/orders/*", "/customers/**", "/shipments/**").hasRole("ADMIN")
                // All other API calls require authentication
                .anyRequest().authenticated()
        );
        // Insert JWT auth filter
        http.addFilterBefore(new JwtTokenFilter(jwtSecret), UsernamePasswordAuthenticationFilter.class);
        http.exceptionHandling().authenticationEntryPoint((req, res, ex) -> res.sendError(401, "Unauthorized"));
        return http.build();
    }
}