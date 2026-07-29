package com.example.api.metrika;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetrikaPropertiesTest {

    @Test
    void offlineImportRequiresCounterId() {
        MetrikaProperties properties = enabledOfflineImport();
        properties.setOauthToken("oauth-token");

        assertThatThrownBy(properties::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("YANDEX_METRIKA_COUNTER_ID");
    }

    @Test
    void offlineImportRequiresOauthToken() {
        MetrikaProperties properties = enabledOfflineImport();
        properties.setCounterId("109831177");

        assertThatThrownBy(properties::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("YANDEX_METRIKA_OAUTH_TOKEN");
    }

    @Test
    void completeOfflineImportConfigurationIsAccepted() {
        MetrikaProperties properties = enabledOfflineImport();
        properties.setCounterId("109831177");
        properties.setOauthToken("oauth-token");

        assertThatCode(properties::validateConfiguration).doesNotThrowAnyException();
    }

    @Test
    void disabledOfflineImportDoesNotRequireCredentials() {
        MetrikaProperties properties = new MetrikaProperties();
        properties.setEnabled(true);

        assertThatCode(properties::validateConfiguration).doesNotThrowAnyException();
    }

    private MetrikaProperties enabledOfflineImport() {
        MetrikaProperties properties = new MetrikaProperties();
        properties.setEnabled(true);
        properties.getOfflineImport().setEnabled(true);
        return properties;
    }
}
