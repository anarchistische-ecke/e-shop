package com.example.api.catalog;

import com.example.catalog.service.ProductSlugNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductSlugNormalizerTest {

    @Test
    void normalizesNewUrlSlugsWithoutBreakingUnicode() {
        assertThat(ProductSlugNormalizer.normalize("  АБЕЛАРД для дома!  "))
                .isEqualTo("абелард-для-дома");
        assertThat(ProductSlugNormalizer.normalize("Linen__Duvet 200x220"))
                .isEqualTo("linen-duvet-200x220");
    }

    @Test
    void rejectsEmptyAndSentenceLengthSlugs() {
        assertThatThrownBy(() -> ProductSlugNormalizer.normalize("..."))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProductSlugNormalizer.normalize("x".repeat(121)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("120");
    }
}
