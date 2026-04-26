package com.example.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
}
