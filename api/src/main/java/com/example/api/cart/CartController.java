package com.example.api.cart;

import com.example.cart.domain.Cart;
import com.example.cart.domain.CartItem;
import com.example.cart.service.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/carts")
public class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);
    private final CartService cartService;

    @Autowired
    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    public ResponseEntity<Cart> createCart(@RequestBody(required = false) CreateCartRequest request) {
        UUID customerId = request != null ? request.getCustomerId() : null;
        Cart cart = cartService.createCart(customerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(cart);
    }

    @PostMapping("/{cartId}/items")
    public ResponseEntity<CartItem> addItemToCart(@PathVariable UUID cartId,
                                                  @Valid @RequestBody CartItemRequest request) {
        try {
            CartItem item = cartService.addItem(cartId, request.getVariantId(), request.getQuantity());
            return ResponseEntity.status(HttpStatus.CREATED).body(item);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("/{cartId}/items/{itemId}")
    public ResponseEntity<Void> updateCartItemQuantity(@PathVariable UUID cartId,
                                                       @PathVariable UUID itemId,
                                                       @Valid @RequestBody UpdateQuantityRequest request) {
        try {
            cartService.updateItemQuantity(cartId, itemId, request.getQuantity());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
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

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> handleRedisExceptions(DataAccessException ex) {
        log.error("Redis error while handling cart request", ex);
        Map<String, String> error = new HashMap<>();
        error.put("error", "Redis unavailable");
        error.put("details", ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    public static class CreateCartRequest {
        private UUID customerId;

        public UUID getCustomerId() {
            return customerId;
        }

        public void setCustomerId(UUID customerId) {
            this.customerId = customerId;
        }
    }

    public static class CartItemRequest {
        private UUID variantId;
        @Min(1)
        private int quantity;

        public UUID getVariantId() {
            return variantId;
        }

        public void setVariantId(UUID variantId) {
            this.variantId = variantId;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }

    public static class UpdateQuantityRequest {
        @Min(1)
        private int quantity;

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}
