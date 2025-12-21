package com.example.cart.service;

import com.example.cart.domain.Cart;
import com.example.cart.domain.CartItem;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.repository.ProductVariantRepository;
import com.example.common.domain.Money;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class CartService {
    private static final Duration CART_TTL = Duration.ofDays(30);
    private final RedisTemplate<String, Cart> cartRedisTemplate;
    private final ProductVariantRepository variantRepository;

    @Autowired
    public CartService(RedisTemplate<String, Cart> cartRedisTemplate,
                       ProductVariantRepository variantRepository) {
        this.cartRedisTemplate = cartRedisTemplate;
        this.variantRepository = variantRepository;
    }

    private String key(UUID cartId) {
        return "cart:" + cartId;
    }

    private void stampBaseEntity(com.example.common.domain.BaseEntity entity, boolean isNew) {
        OffsetDateTime now = OffsetDateTime.now();
        if (isNew && entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
    }

    private Cart persistCart(Cart cart, boolean isNew) {
        stampBaseEntity(cart, isNew);
        cartRedisTemplate.opsForValue().set(key(cart.getId()), cart, CART_TTL);
        return cart;
    }

    public Cart createCart(UUID customerId) {
        Cart cart = new Cart(customerId);
        cart.setId(UUID.randomUUID());
        return persistCart(cart, true);
    }

    private Cart requireCart(UUID cartId) {
        Cart cart = cartRedisTemplate.opsForValue().get(key(cartId));
        if (cart == null) {
            cart = new Cart(null);
            cart.setId(cartId);
            persistCart(cart, true);
        }
        return cart;
    }

    public CartItem addItem(UUID cartId, UUID variantId, int quantity) {
        Cart cart = requireCart(cartId);
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Variant not found: " + variantId));
        Optional<CartItem> existing = cart.getItems().stream()
                .filter(ci -> ci.getVariantId().equals(variantId))
                .findFirst();
        int newQuantity = existing.map(ci -> ci.getQuantity() + quantity).orElse(quantity);
        int available = variant.getStockQuantity();
        if (available > 0 && newQuantity > available) {
            throw new IllegalStateException("Недостаточно запаса на складе. Доступно: " + available);
        }
        Money price = variant.getPrice();

        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(newQuantity);
            stampBaseEntity(item, false);
        } else {
            CartItem item = new CartItem(variantId, quantity, price);
            item.setId(UUID.randomUUID());
            stampBaseEntity(item, true);
            cart.addItem(item);
        }
        persistCart(cart, false);
        return existing.orElseGet(() -> cart.getItems().stream().filter(ci -> ci.getVariantId().equals(variantId)).findFirst().get());
    }

    public Cart getCartById(UUID cartId) {
        Cart cart = requireCart(cartId);
        persistCart(cart, false);
        return cart;
    }

    public void removeItem(UUID cartId, UUID itemId) {
        Cart cart = requireCart(cartId);
        CartItem item = cart.getItems().stream()
                .filter(ci -> itemId.equals(ci.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found: " + itemId));
        cart.removeItem(item);
        persistCart(cart, false);
    }

    public void updateItemQuantity(UUID cartId, UUID itemId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        Cart cart = requireCart(cartId);
        CartItem item = cart.getItems().stream()
                .filter(ci -> itemId.equals(ci.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found: " + itemId));
        ProductVariant variant = variantRepository.findById(item.getVariantId())
                .orElseThrow(() -> new IllegalArgumentException("Variant not found: " + item.getVariantId()));
        int available = variant.getStockQuantity();
        if (available > 0 && quantity > available) {
            throw new IllegalStateException("Недостаточно запаса на складе. Доступно: " + available);
        }
        item.setQuantity(quantity);
        stampBaseEntity(item, false);
        persistCart(cart, false);
    }

    public long calculateCartTotal(UUID cartId) {
        Cart cart = requireCart(cartId);
        return cart.getItems().stream().mapToLong(CartItem::getTotalAmount).sum();
    }

    public void clearCart(UUID cartId) {
        cartRedisTemplate.delete(key(cartId));
    }
}
