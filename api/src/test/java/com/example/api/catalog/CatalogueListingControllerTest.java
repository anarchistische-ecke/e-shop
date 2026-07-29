package com.example.api.catalog;

import com.example.catalog.domain.Brand;
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
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.hasLength;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CatalogueListingControllerTest {

    @Mock
    private CatalogService catalogService;

    @Mock
    private CatalogueResponseFactory responseFactory;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new CatalogueListingController(catalogService, responseFactory)
        ).build();
    }

    @Test
    void returnsOneCompactPageWithPreFilterFacets() throws Exception {
        Category category = new Category("КПБ", "", "kpb", null);
        category.setId(UUID.randomUUID());
        category.setFullPath("kpb");
        Brand brand = new Brand("Cozy", "", "cozy");
        brand.setId(UUID.randomUUID());

        Product first = product("Сатиновый комплект", "satin-set", brand, category, 320_000L, 7);
        first.setDescription("<p>" + "мягкий сатин ".repeat(30) + "</p>");
        Product second = product("Льняной комплект", "linen-set", brand, category, 590_000L, 0);
        ProductImage image = new ProductImage(first, "https://cdn.example/first.jpg", "first", 0);
        image.setId(UUID.randomUUID());

        when(catalogService.getProducts("kpb", null)).thenReturn(List.of(first, second));
        when(catalogService.getPrimaryProductImages(List.of(first.getId(), second.getId())))
                .thenReturn(java.util.Map.of(first.getId(), image));
        when(catalogService.listAllInCategory()).thenReturn(List.of(category));
        when(catalogService.getBySlug("kpb")).thenReturn(java.util.Optional.of(category));
        when(responseFactory.toVariantResponse(any(ProductVariant.class)))
                .thenAnswer(invocation -> variantResponse(invocation.getArgument(0)));
        when(responseFactory.toImageResponse(image)).thenReturn(imageResponse(image));
        when(responseFactory.deserializeSpecifications(any())).thenReturn(List.of());

        mockMvc.perform(get("/catalogue/listing")
                        .param("category", "kpb")
                        .param("inStock", "true")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=60, public"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Сатиновый комплект"))
                .andExpect(jsonPath("$.items[0].images.length()").value(1))
                .andExpect(jsonPath("$.items[0].variants.length()").value(1))
                .andExpect(jsonPath("$.items[0].summary").value(hasLength(160)))
                .andExpect(jsonPath("$.items[0].badges[0]").value("in_stock"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.facets.price.minMinor").value(320_000L))
                .andExpect(jsonPath("$.facets.price.maxMinor").value(590_000L))
                .andExpect(jsonPath("$.facets.brands[0].count").value(2));
    }

    private Product product(
            String name,
            String slug,
            Brand brand,
            Category category,
            long amount,
            int stock
    ) {
        Product product = new Product(name, name, slug);
        product.setId(UUID.randomUUID());
        product.setBrand(brand);
        product.setCategories(Set.of(category));
        ProductVariant variant = new ProductVariant(
                slug.toUpperCase(),
                "Основной",
                Money.of(amount, "RUB"),
                stock
        );
        variant.setId(UUID.randomUUID());
        product.addVariant(variant);
        return product;
    }

    private CatalogController.VariantResponse variantResponse(ProductVariant variant) {
        CatalogController.VariantResponse response = new CatalogController.VariantResponse();
        response.setId(variant.getId());
        response.setName(variant.getName());
        response.setPrice(variant.getPrice());
        response.setStock(variant.getStockQuantity());
        return response;
    }

    private CatalogController.ImageResponse imageResponse(ProductImage image) {
        CatalogController.ImageResponse response = new CatalogController.ImageResponse();
        response.setId(image.getId());
        response.setUrl(image.getUrl());
        return response;
    }
}
