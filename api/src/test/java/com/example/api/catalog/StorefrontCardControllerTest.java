package com.example.api.catalog;

import com.example.catalog.domain.Category;
import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductImage;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.service.CatalogService;
import com.example.common.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StorefrontCardControllerTest {

    @Mock
    private CatalogService catalogService;

    @Mock
    private CatalogueResponseFactory responseFactory;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StorefrontCardController(catalogService, responseFactory)).build();
    }

    @Test
    void getCardsReturnsCompactProductAndCategoryPayloads() throws Exception {
        Product product = new Product("Linen Duvet", "Long product description", "linen-duvet");
        product.setId(UUID.randomUUID());
        ProductVariant variant = new ProductVariant("LINEN-1", "200x220", Money.of(129900, "RUB"), 7);
        variant.setId(UUID.randomUUID());
        variant.setProduct(product);
        product.setVariants(java.util.Set.of(variant));

        ProductImage image = new ProductImage(
                product,
                "https://storage.yandexcloud.net/bucket/products/product-id/image-id.jpeg",
                "products/product-id/image-id.jpeg",
                0
        );
        image.setId(UUID.randomUUID());

        Category category = new Category("Bedroom", "Bedroom category", "bedroom", null);
        category.setId(UUID.randomUUID());
        category.setImageUrl("https://storage.yandexcloud.net/bucket/categories/category-id/image-id.png");
        category.setIsActive(true);

        CatalogController.VariantResponse variantResponse = new CatalogController.VariantResponse();
        variantResponse.setPrice(Money.of(129900, "RUB"));
        variantResponse.setStock(7);

        CatalogController.ImageResponse imageResponse = new CatalogController.ImageResponse();
        imageResponse.setId(image.getId());
        imageResponse.setUrl(image.getUrl());
        imageResponse.setMedia(media("https://img.example.test/media/products/product-id/image-id/w640.webp"));

        when(catalogService.getProductBySlug("linen-duvet")).thenReturn(Optional.of(product));
        when(catalogService.getProductImages(product.getId())).thenReturn(List.of(image));
        when(catalogService.getBySlug("bedroom")).thenReturn(Optional.of(category));
        when(catalogService.getProducts("bedroom", null)).thenReturn(List.of(product));
        when(responseFactory.toVariantResponse(variant)).thenReturn(variantResponse);
        when(responseFactory.toImageResponse(image)).thenReturn(imageResponse);
        when(responseFactory.toMediaManifest(image, product.getName())).thenReturn(imageResponse.getMedia());
        when(responseFactory.toMediaManifest(category.getImageUrl(), category.getName()))
                .thenReturn(media("https://img.example.test/media/categories/category-id/image-id/w640.webp"));

        mockMvc.perform(get("/catalogue/cards")
                        .queryParam("productKeys", "linen-duvet")
                        .queryParam("categoryKeys", "bedroom"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.compact").value(true))
                .andExpect(jsonPath("$.products[0].slug").value("linen-duvet"))
                .andExpect(jsonPath("$.products[0].stock").value(7))
                .andExpect(jsonPath("$.products[0].images[0].media.url").value("https://img.example.test/media/products/product-id/image-id/w640.webp"))
                .andExpect(jsonPath("$.products[0].specifications").doesNotExist())
                .andExpect(jsonPath("$.categories[0].slug").value("bedroom"))
                .andExpect(jsonPath("$.categories[0].productCount").value(1))
                .andExpect(jsonPath("$.categories[0].media.url").value("https://img.example.test/media/categories/category-id/image-id/w640.webp"));
    }

    private MediaModels.MediaManifest media(String url) {
        return new MediaModels.MediaManifest(
                url,
                "https://storage.example.test/original.jpg",
                "",
                null,
                null,
                Map.of("webp", List.of(new MediaModels.MediaVariant(url, 640, "webp")))
        );
    }
}
