package com.example.api.catalog;

import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductImage;
import com.example.catalog.service.CatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CatalogControllerProductImagePreviewTest {

    @Mock
    private CatalogService catalogService;

    @Mock
    private ProductImageStorageService imageStorageService;

    @Mock
    private CatalogueResponseFactory responseFactory;

    @Mock
    private CataloguePresentationService presentationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CatalogController(
                catalogService,
                imageStorageService,
                responseFactory,
                presentationService
        )).build();
    }

    @Test
    void previewProductImageStreamsCachedOriginalObject() throws Exception {
        UUID imageId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        ProductImage image = new ProductImage(
                new Product("Cloud Sofa", "Soft", "cloud-sofa"),
                "https://storage.example.test/products/product-id/image-id.jpeg",
                "products/product-id/image-id.jpeg",
                0
        );
        byte[] bytes = "jpeg-content".getBytes(StandardCharsets.UTF_8);

        when(catalogService.getProductImage(imageId)).thenReturn(image);
        when(imageStorageService.download(image.getObjectKey()))
                .thenReturn(new ProductImageStorageService.StoredImageContent(bytes, "image/jpeg"));

        mockMvc.perform(get("/products/images/{imageId}/preview", imageId))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"))
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(bytes));
    }
}
