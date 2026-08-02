package com.example.api.cart;

import com.example.api.catalog.CatalogController;
import com.example.api.catalog.CatalogueResponseFactory;
import com.example.api.catalog.MediaModels;
import com.example.cart.domain.Cart;
import com.example.cart.domain.CartItem;
import com.example.cart.service.CartPricingSummary;
import com.example.cart.service.CartService;
import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductImage;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.service.CatalogService;
import com.example.common.domain.Money;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/carts")
public class CartViewController {

    private final CartService cartService;
    private final CatalogService catalogService;
    private final CatalogueResponseFactory responseFactory;

    public CartViewController(
            CartService cartService,
            CatalogService catalogService,
            CatalogueResponseFactory responseFactory
    ) {
        this.cartService = cartService;
        this.catalogService = catalogService;
        this.responseFactory = responseFactory;
    }

    @GetMapping("/{cartId}/view")
    public ResponseEntity<CartViewResponse> getCartView(@PathVariable UUID cartId) {
        Cart cart = cartService.getCartById(cartId);
        CartPricingSummary pricing = cartService.calculateCartPricing(cartId);
        List<CartViewItem> items = cart.getItems().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(CartItem::getId))
                .map(item -> toViewItem(item, pricing))
                .toList();
        return ResponseEntity.ok(new CartViewResponse(cartId, cart.getPromoCode(), items, pricing));
    }

    private CartViewItem toViewItem(CartItem item, CartPricingSummary pricing) {
        Optional<ProductVariant> variant = catalogService.getVariant(item.getVariantId());
        Product product = variant.map(ProductVariant::getProduct).orElse(null);
        ProductImage primaryImage = product == null
                ? null
                : catalogService.getProductImages(product.getId()).stream().findFirst().orElse(null);
        CartPricingSummary.CartPricingLine pricingLine = pricing.items().stream()
                .filter(line -> Objects.equals(line.variantId(), item.getVariantId()))
                .findFirst()
                .orElse(null);

        ProductInfo productInfo = product == null
                ? null
                : new ProductInfo(
                        product.getId(),
                        product.getSlug(),
                        product.getName(),
                        variant.map(ProductVariant::getName).orElse(null),
                        variant.map(ProductVariant::getSizeLabel).orElse(null),
                        variant.map(ProductVariant::getColorLabel).orElse(null),
                        primaryImage != null ? responseFactory.toImageResponse(primaryImage) : null,
                        primaryImage != null
                                ? responseFactory.toMediaManifest(primaryImage, product.getName())
                                : null
                );

        return new CartViewItem(
                item.getId(),
                item.getVariantId(),
                item.getQuantity(),
                item.getUnitPrice(),
                productInfo,
                pricingLine
        );
    }

    public record CartViewResponse(
            UUID cartId,
            String promoCode,
            List<CartViewItem> items,
            CartPricingSummary pricing
    ) {
    }

    public record CartViewItem(
            UUID id,
            UUID variantId,
            int quantity,
            Money unitPrice,
            ProductInfo product,
            CartPricingSummary.CartPricingLine pricing
    ) {
    }

    public record ProductInfo(
            UUID id,
            String slug,
            String name,
            String variantName,
            String size,
            String color,
            CatalogController.ImageResponse image,
            MediaModels.MediaManifest primaryMedia
    ) {
    }
}
