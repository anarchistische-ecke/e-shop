package com.example.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String WILDCARD_HEADER = "*";
    private static final String[] REQUIRED_ALLOWED_HEADERS = {
            "Authorization",
            "Content-Type",
            "Idempotency-Key"
    };

    @Value("${cors.allowed-origins:*}")
    private String allowedOrigins;

    @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}")
    private String allowedMethods;

    @Value("${cors.allowed-headers:Authorization,Content-Type,Idempotency-Key}")
    private String allowedHeaders;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(splitAndTrim(allowedOrigins))
                .allowedMethods(splitAndTrim(allowedMethods))
                .allowedHeaders(resolveAllowedHeaders());
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(splitAndTrim(allowedOrigins)));
        configuration.setAllowedMethods(Arrays.asList(splitAndTrim(allowedMethods)));
        configuration.setAllowedHeaders(Arrays.asList(resolveAllowedHeaders()));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    String[] resolveAllowedHeaders() {
        Set<String> headers = new LinkedHashSet<>();
        Set<String> normalizedHeaders = new LinkedHashSet<>();
        for (String header : splitAndTrim(allowedHeaders)) {
            if (WILDCARD_HEADER.equals(header)) {
                return new String[] {WILDCARD_HEADER};
            }
            headers.add(header);
            normalizedHeaders.add(header.toLowerCase(Locale.ROOT));
        }
        for (String requiredHeader : REQUIRED_ALLOWED_HEADERS) {
            if (!normalizedHeaders.contains(requiredHeader.toLowerCase(Locale.ROOT))) {
                headers.add(requiredHeader);
            }
        }
        return headers.toArray(String[]::new);
    }

    private String[] splitAndTrim(String csv) {
        if (csv == null || csv.isBlank()) {
            return new String[0];
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
    }
}
