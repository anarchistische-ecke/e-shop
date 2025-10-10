package com.example.cart.service;

import com.example.cart.domain.Cart;
import com.example.cart.domain.CartItem;
import com.example.cart.repository.CartItemRepository;
import com.example.cart.repository.CartRepository;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.repository.ProductVariantRepository;
import com.example.common.domain.Money;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class CartService {
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;

    @Autowired
    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       ProductVariantRepository variantRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.variantRepository = variantRepository;
    }

    public Cart createCart(UUID customerId) {
        Cart cart = new Cart(customerId);
        return cartRepository.save(cart);
    }

    public CartItem addItem(UUID cartId, UUID variantId, int quantity) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found: " + cartId));
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Variant not found: " + variantId));
        // use the variant's current price
        Money price = variant.getPrice();
        CartItem item = new CartItem(variantId, quantity, price);
        cart.addItem(item);
        cartRepository.save(cart);
        return item;
    }

    public Cart getCartById(UUID cartId) {
        return cartRepository.findById(cartId)
                .orElseThrow(() -> new EntityNotFoundException("Такой корзины нет" + cartId));
    }

    public void removeItem(UUID cartId, UUID itemId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found: " + cartId));
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found: " + itemId));
        cart.removeItem(item);
        cartRepository.save(cart);
    }

    public void updateItemQuantity(UUID cartId, UUID itemId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found: " + cartId));
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found: " + itemId));
        if (!item.getCart().getId().equals(cart.getId())) {
            throw new IllegalStateException("Item does not belong to the specified cart");
        }
        item.setQuantity(quantity);
        cartItemRepository.save(item);
    }

    public long calculateCartTotal(UUID cartId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found: " + cartId));
        return cart.getItems().stream().mapToLong(CartItem::getTotalAmount).sum();
    }
}