package com.example.api.catalog;

import com.example.catalog.domain.Category;
import com.example.catalog.domain.Product;
import com.example.catalog.repository.BrandRepository;
import com.example.catalog.repository.CategoryRepository;
import com.example.catalog.repository.ProductImageRepository;
import com.example.catalog.repository.ProductRepository;
import com.example.catalog.repository.ProductVariantRepository;
import com.example.catalog.service.CatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceLegacySlugTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductVariantRepository variantRepository;
    @Mock
    private BrandRepository brandRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductImageRepository imageRepository;

    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new CatalogService(
                productRepository,
                variantRepository,
                brandRepository,
                categoryRepository,
                imageRepository
        );
    }

    @Test
    void productLookupFallsBackToTrimmedCaseInsensitiveLegacySlug() {
        Product product = new Product("Cassete", "", "Жемчуг пепел ");
        when(productRepository.findBySlug("жемчуг ПЕПЕЛ")).thenReturn(Optional.empty());
        when(productRepository.findByNormalizedSlug("жемчуг ПЕПЕЛ")).thenReturn(List.of(product));

        assertThat(catalogService.getProductBySlug("жемчуг ПЕПЕЛ")).contains(product);
    }

    @Test
    void categoryLookupFallsBackToTrimmedCaseInsensitiveLegacySlug() {
        Category category = new Category();
        category.setSlug("blankets");
        when(categoryRepository.findBySlug("Blankets")).thenReturn(Optional.empty());
        when(categoryRepository.findByNormalizedSlug("Blankets")).thenReturn(List.of(category));

        assertThat(catalogService.getBySlug("Blankets")).contains(category);
    }
}
