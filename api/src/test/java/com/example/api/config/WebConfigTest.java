package com.example.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class WebConfigTest {

    @Test
    void allowedHeadersAlwaysIncludeCheckoutIdempotencyHeader() {
        WebConfig config = new WebConfig();
        ReflectionTestUtils.setField(config, "allowedHeaders", "Authorization,Content-Type");

        assertThat(config.resolveAllowedHeaders())
                .containsExactly("Authorization", "Content-Type", "Idempotency-Key");
    }

    @Test
    void wildcardAllowedHeadersRemainWildcard() {
        WebConfig config = new WebConfig();
        ReflectionTestUtils.setField(config, "allowedHeaders", "*");

        assertThat(config.resolveAllowedHeaders()).containsExactly("*");
    }

    @Test
    void securityCorsSourceIncludesCheckoutIdempotencyHeader() {
        WebConfig config = new WebConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", "https://yug-postel.ru");
        ReflectionTestUtils.setField(config, "allowedMethods", "GET,POST,OPTIONS");
        ReflectionTestUtils.setField(config, "allowedHeaders", "Authorization,Content-Type");
        CorsConfigurationSource source = config.corsConfigurationSource();

        CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/orders/checkout"));

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedHeaders()).containsExactly("Authorization", "Content-Type", "Idempotency-Key");
    }
}
