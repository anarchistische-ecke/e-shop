package com.example.api.content;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CmsHtmlSanitizerTest {

    private final CmsHtmlSanitizer sanitizer = new CmsHtmlSanitizer();

    @Test
    void removesScriptsEventHandlersAndUnsafeProtocols() {
        String result = sanitizer.sanitize(
                "<p onclick=\"steal()\">Text<script>alert(1)</script>"
                        + "<a href=\"javascript:alert(2)\">bad</a>"
                        + "<a href=\"https://example.com/path\">safe</a>"
                        + "<a href=\"#delivery\">anchor</a></p>"
        );

        assertThat(result)
                .contains("<p>Text")
                .contains("href=\"https://example.com/path\"")
                .contains("href=\"#delivery\"")
                .contains("rel=\"noopener noreferrer\"")
                .doesNotContain("script", "onclick", "javascript:");
    }

    @Test
    void permitsInternalHttpsMailTelephoneAndAnchorLinksOnly() {
        assertThat(sanitizer.isSafeLink("/catalog?tag=sale")).isTrue();
        assertThat(sanitizer.isSafeLink("#delivery")).isTrue();
        assertThat(sanitizer.isSafeLink("https://example.com")).isTrue();
        assertThat(sanitizer.isSafeLink("mailto:help@example.com")).isTrue();
        assertThat(sanitizer.isSafeLink("tel:+79991234567")).isTrue();
        assertThat(sanitizer.isSafeLink("//evil.example")).isFalse();
        assertThat(sanitizer.isSafeLink("http://example.com")).isFalse();
        assertThat(sanitizer.isSafeLink("data:text/html,test")).isFalse();
    }
}
