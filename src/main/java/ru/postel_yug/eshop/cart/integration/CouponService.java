package ru.postel_yug.eshop.cart.integration;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
// Переделать
@Service
public class CouponService {
    public enum DiscountType { PERCENTAGE, FIXED_AMOUNT }

    public static class Coupon {
        private String code;
        private DiscountType discountType;
        private BigDecimal value;

        public Coupon(String code, DiscountType discountType, BigDecimal value) {
            this.code = code;
            this.discountType = discountType;
            this.value = value;
        }

        public String getCode() { return code; }
        public DiscountType getDiscountType() { return discountType; }
        public BigDecimal getValue() { return value; }
    }

    private static final Map<String, Coupon> COUPONS = new HashMap<>();
    static {
        COUPONS.put("SAVE10", new Coupon("SAVE10", DiscountType.PERCENTAGE, BigDecimal.valueOf(10)));
        COUPONS.put("HALFPRICE", new Coupon("HALFPRICE", DiscountType.PERCENTAGE, BigDecimal.valueOf(50)));
        COUPONS.put("WELCOME100", new Coupon("WELCOME100", DiscountType.FIXED_AMOUNT, BigDecimal.valueOf(100)));
    }

    public Coupon getCoupon(String code) {
        return COUPONS.get(code);
    }
}
