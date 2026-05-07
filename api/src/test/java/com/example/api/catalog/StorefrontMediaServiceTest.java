package com.example.api.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorefrontMediaServiceTest {

    @Test
    void imageUrlBuildsDerivativeManifestAndKeepsOriginalFallback() {
        StorefrontMediaService service = new StorefrontMediaService("https://img.example.test/", "/media/");

        MediaModels.MediaManifest manifest = service.imageUrl(
                "https://storage.yandexcloud.net/bucket/products/product-id/image-id.jpeg",
                "Hero"
        );

        assertThat(manifest.originalUrl()).isEqualTo("https://storage.yandexcloud.net/bucket/products/product-id/image-id.jpeg");
        assertThat(manifest.url()).isEqualTo("https://img.example.test/media/products/product-id/image-id/w1600.webp");
        assertThat(manifest.sources()).containsKeys("avif", "webp", "jpeg");
        assertThat(manifest.sources().get("avif")).hasSize(9);
        assertThat(manifest.sources().get("webp").get(0).url())
                .isEqualTo("https://img.example.test/media/products/product-id/image-id/w96.webp");
        assertThat(manifest.alt()).isEqualTo("Hero");
    }
}
