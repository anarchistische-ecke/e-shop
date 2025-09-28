package ru.postel_yug.eshop.cart.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import ru.postel_yug.eshop.cart.entity.Cart;

import java.time.Duration;

@Repository
public class CartRepository {
        private static final String CART_KEY_PREFIX_USER  = "cart:user:";
        private static final String CART_KEY_PREFIX_GUEST = "cart:guest:";

        private static final Duration CART_TTL = Duration.ofDays(30);

        @Autowired
        private RedisTemplate<String, Cart> redisTemplate;

        public void saveCartForUser(String userId, Cart cart) {
            String key = CART_KEY_PREFIX_USER + userId;
            redisTemplate.opsForValue().set(key, cart, CART_TTL);
        }

        public void saveCartForGuest(String guestId, Cart cart) {
            String key = CART_KEY_PREFIX_GUEST + guestId;
            redisTemplate.opsForValue().set(key, cart, CART_TTL);
        }

        public Cart getCartByUserId(String userId) {
            String key = CART_KEY_PREFIX_USER + userId;
            return redisTemplate.opsForValue().get(key);
        }

        public Cart getCartByGuestId(String guestId) {
            String key = CART_KEY_PREFIX_GUEST + guestId;
            return redisTemplate.opsForValue().get(key);
        }

        public void deleteCartByUserId(String userId) {
            String key = CART_KEY_PREFIX_USER + userId;
            redisTemplate.delete(key);
        }

        public void deleteCartByGuestId(String guestId) {
            String key = CART_KEY_PREFIX_GUEST + guestId;
            redisTemplate.delete(key);
        }
}
