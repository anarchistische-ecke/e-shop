package ru.postel_yug.eshop.cart.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.postel_yug.eshop.cart.dto.CartItemRequestDto;
import ru.postel_yug.eshop.cart.entity.Cart;
import ru.postel_yug.eshop.cart.entity.CartItem;
import ru.postel_yug.eshop.cart.integration.CouponService;
import ru.postel_yug.eshop.cart.integration.ProductCatalogService;
import ru.postel_yug.eshop.cart.repository.CartRepository;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class CartService {
    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ProductCatalogService productService;

    @Autowired
    private CouponService couponService;

    public Cart getCart(String userId, String guestId) {
        Cart cart;
        if (userId != null) {
            cart = cartRepository.getCartByUserId(userId);
            if (cart == null) {
                cart = new Cart();
                cartRepository.saveCartForUser(userId, cart);
            }
        } else {
            if (guestId != null) {
                cart = cartRepository.getCartByGuestId(guestId);
            } else {
                cart = null;
            }
            if (cart == null) {
                cart = new Cart();
                guestId = UUID.randomUUID().toString();
                cartRepository.saveCartForGuest(guestId, cart);
            }
        }
        return cart;
    }

    public Cart addItem(String userId, String guestId, CartItemRequestDto itemRequest) {
        Cart cart = getCart(userId, guestId);
        String productId = itemRequest.getProductId();
        int quantityToAdd = itemRequest.getQuantity();
        if (quantityToAdd <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (!productService.isProductAvailable(productId, quantityToAdd)) {
            throw new IllegalArgumentException("Product " + productId + " is not available for purchase");
        }
        ProductCatalogService.Product product = productService.getProductById(productId);
        if (product == null) {
            throw new IllegalArgumentException("Product " + productId + " not found in catalog");
        }

        boolean itemFound = false;
        for (CartItem item : cart.getItems()) {
            if (item.getProductId().equals(productId)) {
                int newQuantity = item.getQuantity() + quantityToAdd;
                if (!productService.isProductAvailable(productId, newQuantity)) {
                    throw new IllegalArgumentException("Not enough stock for product " + productId + " for quantity " + newQuantity);
                }
                item.setQuantity(newQuantity);
                itemFound = true;
                break;
            }
        }
        if (!itemFound) {
            CartItem newItem = new CartItem(product.getId(), product.getName(), quantityToAdd, product.getPrice());
            cart.getItems().add(newItem);
        }
        recalculateCartTotals(cart);

        if (userId != null) {
            cartRepository.saveCartForUser(userId, cart);
        } else {
            if (guestId == null) {
                guestId = findGuestCartId();
            }
            cartRepository.saveCartForGuest(guestId, cart);
        }

        return cart;
    }

    public Cart updateItemQuantity(String userId, String guestId, String productId, int newQuantity) {
        Cart cart = getCart(userId, guestId);
        if (newQuantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero when updating");
        }

        CartItem targetItem = null;
        for (CartItem item : cart.getItems()) {
            if (item.getProductId().equals(productId)) {
                targetItem = item;
                break;
            }
        }
        if (targetItem == null) {
            throw new IllegalArgumentException("Product " + productId + " is not in the cart");
        }

        if (!productService.isProductAvailable(productId, newQuantity)) {
            throw new IllegalArgumentException("Product " + productId + " is not available in quantity " + newQuantity);
        }

        targetItem.setQuantity(newQuantity);
        recalculateCartTotals(cart);

        if (userId != null) {
            cartRepository.saveCartForUser(userId, cart);
        } else {
            cartRepository.saveCartForGuest(guestId, cart);
        }
        return cart;
    }

    public Cart removeItem(String userId, String guestId, String productId) {
        Cart cart = getCart(userId, guestId);
        cart.getItems().removeIf(item -> item.getProductId().equals(productId));
        recalculateCartTotals(cart);
        if (userId != null) {
            cartRepository.saveCartForUser(userId, cart);
        } else {
            cartRepository.saveCartForGuest(guestId, cart);
        }
        return cart;
    }

    public Cart applyCoupon(String userId, String guestId, String couponCode) {
        Cart cart = getCart(userId, guestId);
        if (couponCode == null || couponCode.isEmpty()) {
            throw new IllegalArgumentException("Coupon code cannot be null or empty");
        }

        CouponService.Coupon coupon = couponService.getCoupon(couponCode);
        if (coupon == null) {
            throw new IllegalArgumentException("Coupon code " + couponCode + " is invalid or expired");
        }

        cart.setCouponCode(couponCode);

        recalculateCartTotals(cart);

        if (userId != null) {
            cartRepository.saveCartForUser(userId, cart);
        } else {
            cartRepository.saveCartForGuest(guestId, cart);
        }
        return cart;
    }

    public Cart removeCoupon(String userId, String guestId) {
        Cart cart = getCart(userId, guestId);
        if (cart.getCouponCode() == null) {
            return cart;
        }
        cart.setCouponCode(null);
        recalculateCartTotals(cart);
        if (userId != null) {
            cartRepository.saveCartForUser(userId, cart);
        } else {
            cartRepository.saveCartForGuest(guestId, cart);
        }
        return cart;
    }

    public Cart mergeCarts(String guestId, String userId) {
        if (userId == null || guestId == null) {
            throw new IllegalArgumentException("Both guestId and userId must be provided for merging");
        }
        Cart guestCart = cartRepository.getCartByGuestId(guestId);
        Cart userCart = cartRepository.getCartByUserId(userId);
        if (guestCart == null) {
            if (userCart == null) {
                userCart = new Cart();
            }
            return userCart;
        }
        if (userCart == null) {
            userCart = guestCart;
        } else {
            for (CartItem guestItem : guestCart.getItems()) {
                boolean found = false;
                for (CartItem userItem : userCart.getItems()) {
                    if (userItem.getProductId().equals(guestItem.getProductId())) {
                        int combinedQuantity = userItem.getQuantity() + guestItem.getQuantity();
                        if (!productService.isProductAvailable(userItem.getProductId(), combinedQuantity)) {
                            combinedQuantity = Math.min(combinedQuantity,
                                    productService.getProductById(userItem.getProductId()).getStock());
                        }
                        userItem.setQuantity(combinedQuantity);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    userCart.getItems().add(guestItem);
                }
            }
            if (userCart.getCouponCode() == null && guestCart.getCouponCode() != null) {
                userCart.setCouponCode(guestCart.getCouponCode());
            }
        }
        recalculateCartTotals(userCart);
        cartRepository.saveCartForUser(userId, userCart);
        cartRepository.deleteCartByGuestId(guestId);
        return userCart;
    }
    private void recalculateCartTotals(Cart cart) {
        BigDecimal totalPrice = BigDecimal.ZERO;
        int totalItems = 0;
        int totalQuantity = 0;
        for (CartItem item : cart.getItems()) {
            BigDecimal itemTotal = item.getPricePerUnit().multiply(BigDecimal.valueOf(item.getQuantity()));
            totalPrice = totalPrice.add(itemTotal);
            totalQuantity += item.getQuantity();
            totalItems += 1;
        }
        cart.setTotalPrice(totalPrice);
        cart.setTotalItems(totalItems);
        cart.setTotalQuantity(totalQuantity);
        BigDecimal totalDiscount = BigDecimal.ZERO;
        if (cart.getCouponCode() != null) {
            CouponService.Coupon coupon = couponService.getCoupon(cart.getCouponCode());
            if (coupon != null) {
                if (coupon.getDiscountType() == CouponService.DiscountType.PERCENTAGE) {
                    BigDecimal percent = coupon.getValue();
                    totalDiscount = totalPrice.multiply(percent).divide(BigDecimal.valueOf(100));
                } else if (coupon.getDiscountType() == CouponService.DiscountType.FIXED_AMOUNT) {
                    totalDiscount = coupon.getValue();
                }
                if (totalDiscount.compareTo(totalPrice) > 0) {
                    totalDiscount = totalPrice;
                }
            } else {
                cart.setCouponCode(null);
            }
        }
        cart.setTotalDiscount(totalDiscount);
        BigDecimal totalPriceWithDiscount = totalPrice.subtract(totalDiscount);
        cart.setTotalPriceWithDiscount(totalPriceWithDiscount);
    }
    private String findGuestCartId() {
        return null;
    }

}
