package com.example.api.content;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DirectusMarketingV2ConfigurationVerifier implements InitializingBean {

    private final DirectusContentProperties properties;

    public DirectusMarketingV2ConfigurationVerifier(DirectusContentProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        if (!properties.isMarketingV2Enabled()) {
            return;
        }
        requireText(properties.getBaseUrl(), "DIRECTUS_BASE_URL");
        requireSecret(properties.getStaticToken(), "DIRECTUS_STATIC_TOKEN");
        requireSecret(properties.getPreviewStaticToken(), "DIRECTUS_PREVIEW_TOKEN");
        requireSecret(properties.getPreviewSecret(), "CMS_PREVIEW_SECRET");
    }

    private void requireText(String value, String environmentVariable) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    environmentVariable + " is required when CMS_MARKETING_V2_ENABLED=true"
            );
        }
    }

    private void requireSecret(String value, String environmentVariable) {
        requireText(value, environmentVariable);
        if (value.trim().length() < 32) {
            throw new IllegalStateException(
                    environmentVariable
                            + " must contain at least 32 characters when CMS_MARKETING_V2_ENABLED=true"
            );
        }
    }
}
