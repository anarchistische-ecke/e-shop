package ru.postel_yug.eshop.cart.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.postel_yug.eshop.cart.dto.CartDto;
import ru.postel_yug.eshop.cart.dto.CartItemRequestDto;
import ru.postel_yug.eshop.cart.entity.Cart;
import ru.postel_yug.eshop.cart.entity.CartItem;
import ru.postel_yug.eshop.cart.integration.CouponService;
import ru.postel_yug.eshop.cart.integration.ProductCatalogService;
import ru.postel_yug.eshop.cart.mapper.CartMapper;
import ru.postel_yug.eshop.cart.repository.CartRepository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CartService {

    private final CartMapper cartMapper;
    private final Map<Long, Cart> carts = new ConcurrentHashMap<>();

    public CartService(CartMapper cartMapper) {
        this.cartMapper = cartMapper;
    }

    public CartDto getCartByUserId(Long userId) {
        Cart cart = carts.computeIfAbsent(userId, id -> {
            Cart c = new Cart();
            c.setUserId(userId);
            return c;
        });
        return cartMapper.toDto(cart);
    }

    public CartDto addItemToCart(Long userId, CartItemRequestDto request) {
        Cart cart = carts.computeIfAbsent(userId, id -> {
            Cart c = new Cart();
            c.setUserId(userId);
            return c;
        });
        CartItem existing = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(request.getProductId()))
                .findFirst().orElse(null);

        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + request.getQuantity());
        } else {
            cart.getItems().add(new CartItem(request.getProductId(), request.getQuantity()));
        }
        return cartMapper.toDto(cart);
    }
    
    public CartDto updateItemQuantity(Long userId, Long productId, int quantity) {
        Cart cart = carts.get(userId);
        if (cart != null) {
            cart.getItems().stream()
                    .filter(i -> i.getProductId().equals(productId))
                    .findFirst()
                    .ifPresent(item -> item.setQuantity(quantity));
        }
        return cartMapper.toDto(cart);
    }

    public CartDto removeItem(Long userId, Long productId) {
        Cart cart = carts.get(userId);
        if (cart != null) {
            cart.setItems(
                    cart.getItems().stream()
                            .filter(i -> !i.getProductId().equals(productId))
                            .collect(Collectors.toList()));
        }
        return cartMapper.toDto(cart);
    }

    public void clearCart(Long userId) {
        carts.remove(userId);
    }
}
