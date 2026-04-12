package com.example.api.content;

import org.springframework.util.StringUtils;

import java.util.Locale;

public final class ContentCacheKeys {

    private ContentCacheKeys() {
    }

    public static String siteSettings() {
        return "site-settings";
    }

    public static String navigationAll() {
        return "navigation:all";
    }

    public static String navigation(String placement) {
        return "navigation:" + normalizeToken(placement);
    }

    public static String page(String slug) {
        return "page:" + normalizeToken(slug);
    }

    private static String normalizeToken(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Cache key token is required");
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }
}
