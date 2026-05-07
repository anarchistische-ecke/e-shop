package com.example.api.catalog;

import java.util.List;
import java.util.Map;

public final class MediaModels {

    private MediaModels() {
    }

    public record MediaVariant(
            String url,
            int width,
            String format
    ) {
    }

    public record MediaManifest(
            String url,
            String originalUrl,
            String alt,
            Integer width,
            Integer height,
            Map<String, List<MediaVariant>> sources
    ) {
    }
}
