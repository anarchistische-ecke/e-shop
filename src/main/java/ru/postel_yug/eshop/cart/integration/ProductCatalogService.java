package ru.postel_yug.eshop.cart.integration;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
//Переделать
@Service
public class ProductCatalogService {
    public static class Product {
        private String id;
        private String name;
        private BigDecimal price;
        private boolean active;
        private int stock;

        public Product(String id, String name, BigDecimal price, boolean active, int stock) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.active = active;
            this.stock = stock;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public BigDecimal getPrice() { return price; }
        public boolean isActive() { return active; }
        public int getStock() { return stock; }
    }

    private static final Map<String, Product> PRODUCT_DATA = new HashMap<>();
    static {
        PRODUCT_DATA.put("sku1", new Product("sku1", "Пододеяльник синий, двуспальный",
                BigDecimal.valueOf(2000.00), true, 50));
        PRODUCT_DATA.put("sku2", new Product("sku2", "Простыня белая, евро",
                BigDecimal.valueOf(1500.00), true, 20));
        PRODUCT_DATA.put("sku3", new Product("sku3", "Наволочка декоративная",
                BigDecimal.valueOf(500.00), false, 0));
    }

    public Product getProductById(String productId) {
        return PRODUCT_DATA.get(productId);
    }

    public boolean isProductAvailable(String productId, int requiredQuantity) {
        Product product = PRODUCT_DATA.get(productId);
        if (product == null) {
            return false;
        }
        if (!product.isActive()) {
            return false;
        }
        if (product.getStock() < requiredQuantity) {
            return false;
        }
        return true;
    }
}
