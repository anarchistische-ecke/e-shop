package com.example.api.cart;

import com.example.cart.domain.Cart;
import com.example.cart.domain.CartItem;
import com.example.cart.service.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/carts")
public class CartController {

    private final CartService cartService;

    @Autowired
    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    public ResponseEntity<Cart> createCart(@RequestBody CreateCartRequest request) {
        Cart cart = cartService.createCart(request.getCustomerId());
        return ResponseEntity.status(HttpStatus.CREATED).body(cart);
    }

    @PostMapping("/{cartId}/items")
    public ResponseEntity<CartItem> addItemToCart(@PathVariable UUID cartId,
                                                  @Valid @RequestBody CartItemRequest request) {
        CartItem item = cartService.addItem(cartId, request.getVariantId(), request.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    @PutMapping("/{cartId}/items/{itemId}")
    public ResponseEntity<Void> updateCartItemQuantity(@PathVariable UUID cartId,
                                                       @PathVariable UUID itemId,
                                                       @Valid @RequestBody UpdateQuantityRequest request) {
        cartService.updateItemQuantity(cartId, itemId, request.getQuantity());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{cartId}/items/{itemId}")
    public ResponseEntity<Void> removeItemFromCart(@PathVariable UUID cartId,
                                                   @PathVariable UUID itemId) {
        cartService.removeItem(cartId, itemId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{cartId}/total")
    public ResponseEntity<Map<String, Long>> getCartTotal(@PathVariable UUID cartId) {
        long total = cartService.calculateCartTotal(cartId);
        // Return as JSON: {"totalAmount": <sum>}
        Map<String, Long> response = new HashMap<>();
        response.put("totalAmount", total);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{cartId}")
    public ResponseEntity<Cart> getCartById(@PathVariable UUID cartId) {
        Cart cart = cartService.getCartById(cartId);
        return ResponseEntity.ok(cart);
    }

    public static class CreateCartRequest {
        private UUID customerId;
        public UUID getCustomerId() { return customerId; }
        public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    }

    public static class CartItemRequest {
        private UUID variantId;
        @Min(1)
        private int quantity;
        public UUID getVariantId() { return variantId; }
        public void setVariantId(UUID variantId) { this.variantId = variantId; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }

    public static class UpdateQuantityRequest {
        @Min(1)
        private int quantity;
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
}

