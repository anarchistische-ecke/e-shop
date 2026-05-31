package com.example.api.catalog;

import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductImage;
import com.example.catalog.service.CatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorefrontOpsWorkspaceFactoryTest {

    @Mock
    private CatalogService catalogService;

    @Mock
    private CatalogueResponseFactory responseFactory;

    private StorefrontOpsWorkspaceFactory workspaceFactory;

    @BeforeEach
    void setUp() {
        workspaceFactory = new StorefrontOpsWorkspaceFactory(
                catalogService,
                responseFactory,
                "https://api.example.test/products/images/"
        );
    }

    @Test
    void productWorkspaceUsesCdnPreviewUrlsInsteadOfRawStorageUrls() {
        Product product = new Product("Cloud Sofa", "Soft", "cloud-sofa");
        product.setId(UUID.fromString("11111111-1111-4111-8111-111111111111"));
        ProductImage image = new ProductImage(
                product,
                "https://storage.yandexcloud.net/bucket/products/product-id/image-id.jpeg",
                "products/product-id/image-id.jpeg",
                0
        );
        image.setId(UUID.fromString("22222222-2222-4222-8222-222222222222"));

        String previewUrl = "https://api.example.test/products/images/22222222-2222-4222-8222-222222222222/preview";
        when(catalogService.getProductImages(product.getId())).thenReturn(List.of(image));

        StorefrontOpsWorkspaceModels.ProductSummary summary = workspaceFactory.toProductSummary(product, null);
        StorefrontOpsWorkspaceModels.ProductDetail detail = workspaceFactory.toProductDetail(product, null);

        assertThat(summary.primaryImageUrl()).isEqualTo(previewUrl);
        assertThat(detail.images()).singleElement()
                .extracting(StorefrontOpsWorkspaceModels.ProductImageSummary::url)
                .isEqualTo(previewUrl);
    }
}
