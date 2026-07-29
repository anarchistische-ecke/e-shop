package com.example.api.cart;

import com.example.api.catalog.CatalogController;
import com.example.api.catalog.CatalogueResponseFactory;
import com.example.cart.domain.Cart;
import com.example.cart.domain.CartItem;
import com.example.cart.service.CartPricingSummary;
import com.example.cart.service.CartService;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CartViewControllerTest {

    @Mock
    private CartService cartService;

    @Mock
    private CatalogService catalogService;

    @Mock
    private CatalogueResponseFactory responseFactory;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new CartViewController(cartService, catalogService, responseFactory)
        ).build();
    }

    @Test
    void returnsEnrichedItemsAndPricingTogether() throws Exception {
        UUID cartId = UUID.randomUUID();
        Product product = new Product("Комплект Sand", "", "sand");
        product.setId(UUID.randomUUID());
        ProductVariant variant = new ProductVariant("SAND", "Евро", Money.of(420_000L, "RUB"), 5);
        variant.setId(UUID.randomUUID());
        variant.setSizeLabel("200×220");
        product.addVariant(variant);
        ProductImage image = new ProductImage(product, "https://cdn.example/sand.jpg", "sand", 0);
        image.setId(UUID.randomUUID());

        CartItem item = new CartItem(variant.getId(), 2, Money.of(420_000L, "RUB"));
        item.setId(UUID.randomUUID());
        Cart cart = new Cart(null);
        cart.setId(cartId);
        cart.setItems(Set.of(item));
        CatalogController.ImageResponse imageResponse = new CatalogController.ImageResponse();
        imageResponse.setId(image.getId());
        imageResponse.setUrl(image.getUrl());

        when(cartService.getCartById(cartId)).thenReturn(cart);
        when(cartService.calculateCartPricing(cartId)).thenReturn(CartPricingSummary.empty("RUB"));
        when(catalogService.getVariant(variant.getId())).thenReturn(Optional.of(variant));
        when(catalogService.getProductImages(product.getId())).thenReturn(List.of(image));
        when(responseFactory.toImageResponse(image)).thenReturn(imageResponse);

        mockMvc.perform(get("/carts/{cartId}/view", cartId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").value(cartId.toString()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].product.name").value("Комплект Sand"))
                .andExpect(jsonPath("$.items[0].product.variantName").value("Евро"))
                .andExpect(jsonPath("$.items[0].product.size").value("200×220"))
                .andExpect(jsonPath("$.items[0].product.image.url").value("https://cdn.example/sand.jpg"))
                .andExpect(jsonPath("$.pricing.finalTotal.amount").value(0));
    }
}
