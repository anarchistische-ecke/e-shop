package ru.postel_yug.eshop.cart.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.postel_yug.eshop.cart.dto.CartDto;
import ru.postel_yug.eshop.cart.dto.CartItemRequestDto;
import ru.postel_yug.eshop.cart.entity.Cart;
import ru.postel_yug.eshop.cart.mapper.CartMapper;
import ru.postel_yug.eshop.cart.service.CartService;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    @GetMapping
    public ResponseEntity<CartDto> getCart(@RequestParam(name = "userId", required = false) String userId,
                                           @RequestParam(name = "guestId", required = false) String guestId) {
        Cart cart = cartService.getCart(userId, guestId);
        String cartIdForDto = (userId == null ? guestId : null);
        CartDto responseDto = CartMapper.toCartDto(cart, cartIdForDto);
        return ResponseEntity.ok(responseDto);
    }

    @PostMapping("/item")
    public ResponseEntity<CartDto> addItemToCart(@RequestParam(name = "userId", required = false) String userId,
                                                 @RequestParam(name = "guestId", required = false) String guestId,
                                                 @RequestBody CartItemRequestDto itemRequest) {
        // Генерируем guestId, если пользователь гость и у него ещё нет ID
        if (userId == null && guestId == null) {
            guestId = java.util.UUID.randomUUID().toString();
        }
        Cart cart = cartService.addItem(userId, guestId, itemRequest);
        CartDto responseDto = CartMapper.toCartDto(cart, (userId == null ? guestId : null));
        return ResponseEntity.ok(responseDto);
    }

    @PutMapping("/item/{productId}")
    public ResponseEntity<CartDto> updateItemQuantity(@RequestParam(name = "userId", required = false) String userId,
                                                      @RequestParam(name = "guestId", required = false) String guestId,
                                                      @PathVariable String productId,
                                                      @RequestBody CartItemRequestDto itemRequest) {
        // Здесь в itemRequest ожидаем новое количество
        int newQuantity = itemRequest.getQuantity();
        Cart cart = cartService.updateItemQuantity(userId, guestId, productId, newQuantity);
        CartDto responseDto = CartMapper.toCartDto(cart, (userId == null ? guestId : null));
        return ResponseEntity.ok(responseDto);
    }

    @DeleteMapping("/item/{productId}")
    public ResponseEntity<CartDto> removeItem(@RequestParam(name = "userId", required = false) String userId,
                                              @RequestParam(name = "guestId", required = false) String guestId,
                                              @PathVariable String productId) {
        Cart cart = cartService.removeItem(userId, guestId, productId);
        CartDto responseDto = CartMapper.toCartDto(cart, (userId == null ? guestId : null));
        return ResponseEntity.ok(responseDto);
    }

    @PostMapping("/coupon")
    public ResponseEntity<CartDto> applyCoupon(@RequestParam(name = "userId", required = false) String userId,
                                               @RequestParam(name = "guestId", required = false) String guestId,
                                               @RequestParam String couponCode) {
        Cart cart = cartService.applyCoupon(userId, guestId, couponCode);
        CartDto responseDto = CartMapper.toCartDto(cart, (userId == null ? guestId : null));
        return ResponseEntity.ok(responseDto);
    }

    @DeleteMapping("/coupon")
    public ResponseEntity<CartDto> removeCoupon(@RequestParam(name = "userId", required = false) String userId,
                                                @RequestParam(name = "guestId", required = false) String guestId) {
        Cart cart = cartService.removeCoupon(userId, guestId);
        CartDto responseDto = CartMapper.toCartDto(cart, (userId == null ? guestId : null));
        return ResponseEntity.ok(responseDto);
    }

    @PostMapping("/merge")
    public ResponseEntity<CartDto> mergeCarts(@RequestParam String userId,
                                              @RequestParam String guestId) {
        Cart mergedCart = cartService.mergeCarts(guestId, userId);
        CartDto responseDto = CartMapper.toCartDto(mergedCart, null);
        return ResponseEntity.ok(responseDto);
    }
}


