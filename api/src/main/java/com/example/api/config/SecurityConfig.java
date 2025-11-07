package com.example.api.config;

import com.example.api.config.JwtTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf().disable();
        http.cors(cors -> cors.configurationSource(request -> {
            // Allow all origins for simplicity – adjust as needed for production
            org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
            config.addAllowedOriginPattern("*");
            config.addAllowedMethod("*");
            config.addAllowedHeader("*");
            config.setAllowCredentials(true);
            return config;
        }));
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/auth/**", "/customers/register").permitAll()         // allow login and registration
                .requestMatchers(HttpMethod.POST, "/products/**").hasRole("ADMIN")                      // product creation & variant addition – admin only
                .requestMatchers(HttpMethod.PUT, "/products/**").hasRole("ADMIN")                       // product updates – admin only
                .requestMatchers(HttpMethod.DELETE, "/products/**").hasRole("ADMIN")                    // product deletion – admin only
                .requestMatchers(HttpMethod.GET, "/orders", "/orders/*", "/customers/**", "/shipments/**").hasRole("ADMIN")  // admin-only data
                .anyRequest().authenticated()                                                          // all other API calls require authentication
        );
        // Add JWT authentication filter
        http.addFilterBefore(new JwtTokenFilter(jwtSecret), UsernamePasswordAuthenticationFilter.class);
        // Customize unauthorized and access-denied handling (optional)
        http.exceptionHandling().authenticationEntryPoint((req, res, ex) -> res.sendError(401, "Unauthorized"));
        return http.build();
    }
}
