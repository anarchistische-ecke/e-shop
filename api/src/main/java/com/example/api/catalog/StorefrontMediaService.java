package com.example.api.catalog;

import com.example.catalog.domain.ProductImage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StorefrontMediaService {

    private static final List<Integer> DERIVATIVE_WIDTHS = List.of(96, 160, 320, 480, 640, 768, 960, 1280, 1600);
    private static final List<String> DERIVATIVE_FORMATS = List.of("avif", "webp", "jpeg");

    private final String derivativeBaseUrl;
    private final String derivativePathPrefix;

    public StorefrontMediaService(
            @Value("${media.derivatives.public-base-url:https://img.yug-postel.ru}") String derivativeBaseUrl,
            @Value("${media.derivatives.path-prefix:media}") String derivativePathPrefix
    ) {
        this.derivativeBaseUrl = normalizeBaseUrl(derivativeBaseUrl);
        this.derivativePathPrefix = normalizePathToken(derivativePathPrefix);
    }

    public MediaModels.MediaManifest productImage(ProductImage image, String alt) {
        if (image == null || !StringUtils.hasText(image.getUrl())) {
            return null;
        }

        return manifest(image.getUrl(), image.getObjectKey(), alt, null, null);
    }

    public MediaModels.MediaManifest imageUrl(String imageUrl, String alt) {
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }

        return manifest(imageUrl, null, alt, null, null);
    }

    public MediaModels.MediaManifest manifest(
            String originalUrl,
            String objectKey,
            String alt,
            Integer width,
            Integer height
    ) {
        if (!StringUtils.hasText(originalUrl)) {
            return null;
        }

        String normalizedOriginalUrl = originalUrl.trim();
        String resolvedObjectKey = resolveObjectKey(objectKey, normalizedOriginalUrl);
        Map<String, List<MediaModels.MediaVariant>> sources = buildSources(resolvedObjectKey);

        return new MediaModels.MediaManifest(
                primaryUrl(normalizedOriginalUrl, sources),
                normalizedOriginalUrl,
                StringUtils.hasText(alt) ? alt.trim() : "",
                width,
                height,
                sources
        );
    }

    private String primaryUrl(String originalUrl, Map<String, List<MediaModels.MediaVariant>> sources) {
        List<MediaModels.MediaVariant> webpSources = sources.get("webp");
        if (webpSources != null && !webpSources.isEmpty()) {
            return webpSources.get(webpSources.size() - 1).url();
        }

        List<MediaModels.MediaVariant> jpegSources = sources.get("jpeg");
        if (jpegSources != null && !jpegSources.isEmpty()) {
            return jpegSources.get(jpegSources.size() - 1).url();
        }

        return originalUrl;
    }

    private Map<String, List<MediaModels.MediaVariant>> buildSources(String objectKey) {
        if (!StringUtils.hasText(derivativeBaseUrl) || !StringUtils.hasText(objectKey)) {
            return Map.of();
        }

        String baseKey = stripExtension(objectKey);
        if (!StringUtils.hasText(baseKey)) {
            return Map.of();
        }

        Map<String, List<MediaModels.MediaVariant>> sources = new LinkedHashMap<>();
        for (String format : DERIVATIVE_FORMATS) {
            sources.put(format, DERIVATIVE_WIDTHS.stream()
                    .map(width -> new MediaModels.MediaVariant(
                            derivativeUrl(baseKey, width, format),
                            width,
                            format
                    ))
                    .toList());
        }
        return sources;
    }

    private String derivativeUrl(String baseKey, int width, String format) {
        return derivativeBaseUrl
                + "/"
                + derivativePathPrefix
                + "/"
                + baseKey
                + "/w"
                + width
                + "."
                + format;
    }

    private String resolveObjectKey(String objectKey, String originalUrl) {
        if (StringUtils.hasText(objectKey)) {
            return normalizeObjectKey(objectKey);
        }

        String path = extractPath(originalUrl);
        for (String marker : List.of("/products/", "/categories/")) {
            int markerIndex = path.indexOf(marker);
            if (markerIndex >= 0) {
                return normalizeObjectKey(path.substring(markerIndex + 1));
            }
        }

        return "";
    }

    private String extractPath(String originalUrl) {
        try {
            return URI.create(originalUrl).getPath();
        } catch (IllegalArgumentException error) {
            int queryIndex = originalUrl.indexOf('?');
            String withoutQuery = queryIndex >= 0 ? originalUrl.substring(0, queryIndex) : originalUrl;
            int protocolIndex = withoutQuery.indexOf("://");
            if (protocolIndex < 0) {
                return withoutQuery;
            }
            int firstPathSlash = withoutQuery.indexOf('/', protocolIndex + 3);
            return firstPathSlash >= 0 ? withoutQuery.substring(firstPathSlash) : "";
        }
    }

    private String normalizeObjectKey(String value) {
        return StringUtils.hasText(value)
                ? value.trim().replace('\\', '/').replaceAll("^/+", "")
                : "";
    }

    private String stripExtension(String objectKey) {
        String normalizedKey = normalizeObjectKey(objectKey);
        int extensionIndex = normalizedKey.lastIndexOf('.');
        if (extensionIndex <= normalizedKey.lastIndexOf('/')) {
            return normalizedKey;
        }
        return normalizedKey.substring(0, extensionIndex);
    }

    private String normalizeBaseUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String normalizePathToken(String value) {
        if (!StringUtils.hasText(value)) {
            return "media";
        }
        return value.trim()
                .replace('\\', '/')
                .replaceAll("^/+", "")
                .replaceAll("/+$", "")
                .toLowerCase(Locale.ROOT);
    }
}
