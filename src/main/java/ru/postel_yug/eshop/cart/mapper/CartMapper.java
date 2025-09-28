package ru.postel_yug.eshop.cart.mapper;

import ru.postel_yug.eshop.cart.dto.CartDto;
import ru.postel_yug.eshop.cart.dto.CartItemDto;
import ru.postel_yug.eshop.cart.entity.Cart;
import ru.postel_yug.eshop.cart.entity.CartItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CartMapper {

    public static CartDto toCartDto(Cart cart, String cartId) {
        CartDto dto = new CartDto();
        dto.setCartId(cartId);
        // Преобразуем список CartItem в CartItemDto
        List<CartItemDto> itemDtos = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            CartItemDto itemDto = toCartItemDto(item);
            itemDtos.add(itemDto);
        }
        dto.setItems(itemDtos);
        dto.setCouponCode(cart.getCouponCode());
        dto.setTotalPrice(cart.getTotalPrice());
        dto.setTotalDiscount(cart.getTotalDiscount());
        dto.setTotalPriceWithDiscount(cart.getTotalPriceWithDiscount());
        dto.setTotalItems(cart.getTotalItems());
        dto.setTotalQuantity(cart.getTotalQuantity());
        return dto;
    }

    public static CartItemDto toCartItemDto(CartItem item) {

        BigDecimal totalPrice = item.getPricePerUnit().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new CartItemDto(
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getPricePerUnit(),
                totalPrice
        );
    }
}
