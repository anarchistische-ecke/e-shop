package com.example.api.content;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectusMarketingV2ConfigurationVerifierTest {

    @Test
    void allowsBackwardCompatibleDeploymentWhileFeatureIsDisabled() {
        DirectusContentProperties properties = new DirectusContentProperties();
        properties.setMarketingV2Enabled(false);

        assertThatCode(
                () -> new DirectusMarketingV2ConfigurationVerifier(properties).afterPropertiesSet()
        ).doesNotThrowAnyException();
    }

    @Test
    void requiresSeparateServerCredentialsAndPreviewSecretAtCutover() {
        DirectusContentProperties properties = validProperties();
        properties.setPreviewStaticToken("");

        assertThatThrownBy(
                () -> new DirectusMarketingV2ConfigurationVerifier(properties).afterPropertiesSet()
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DIRECTUS_PREVIEW_TOKEN");
    }

    @Test
    void acceptsCompleteCommercialConfiguration() {
        DirectusContentProperties properties = validProperties();

        assertThatCode(
                () -> new DirectusMarketingV2ConfigurationVerifier(properties).afterPropertiesSet()
        ).doesNotThrowAnyException();
    }

    private DirectusContentProperties validProperties() {
        DirectusContentProperties properties = new DirectusContentProperties();
        properties.setMarketingV2Enabled(true);
        properties.setBaseUrl("http://directus:8055");
        properties.setStaticToken("published-reader-token-with-32-characters");
        properties.setPreviewStaticToken("preview-reader-token-with-32-characters");
        properties.setPreviewSecret("preview-hmac-secret-with-32-characters");
        return properties;
    }
}
