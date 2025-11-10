package com.example.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:*}")
    private String allowedOrigins;

    @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}")
    private String allowedMethods;

    @Value("${cors.allowed-headers:Authorization,Content-Type}")
    private String allowedHeaders;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(splitAndTrim(allowedOrigins))
                .allowedMethods(splitAndTrim(allowedMethods))
                .allowedHeaders(splitAndTrim(allowedHeaders));
    }

    private String[] splitAndTrim(String csv) {
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .toArray(String[]::new);
    }
}

