package com.example.catalog.service;

import java.text.Normalizer;
import java.util.Locale;

public final class ProductSlugNormalizer {

    private static final int MAX_LENGTH = 120;

    private ProductSlugNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Product slug must not be blank");
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_]+", "-")
                .replaceAll("[^\\p{L}\\p{N}-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Product slug must contain letters or numbers");
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Product slug must not exceed " + MAX_LENGTH + " characters");
        }
        return normalized;
    }
}
