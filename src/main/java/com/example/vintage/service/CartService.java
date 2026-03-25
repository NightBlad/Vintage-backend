package com.example.vintage.service;

import com.example.vintage.entity.Product;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.SessionScope;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@SessionScope
public class CartService {

    private Map<Long, Integer> cart = new HashMap<>();
    private Map<Long, Product> products = new HashMap<>();

    public void addToCart(Product product, Integer quantity) {
        Long productId = product.getId();

        // Lưu thông tin sản phẩm
        products.put(productId, product);

        // Cập nhật số lượng
        cart.put(productId, cart.getOrDefault(productId, 0) + quantity);
    }

    public void updateCart(Product product, Integer quantity) {
        Long productId = product.getId();

        if (quantity <= 0) {
            removeFromCart(product);
        } else {
            products.put(productId, product);
            cart.put(productId, quantity);
        }
    }

    public void removeFromCart(Product product) {
        Long productId = product.getId();
        cart.remove(productId);
        products.remove(productId);
    }

    public void clearCart() {
        cart.clear();
        products.clear();
    }

    public Map<Product, Integer> getCartItems() {
        Map<Product, Integer> cartItems = new HashMap<>();

        for (Map.Entry<Long, Integer> entry : cart.entrySet()) {
            Long productId = entry.getKey();
            Integer quantity = entry.getValue();
            Product product = products.get(productId);

            if (product != null) {
                cartItems.put(product, quantity);
            }
        }

        return cartItems;
    }

    public int getTotalItems() {
        return cart.values().stream().mapToInt(Integer::intValue).sum();
    }

    public BigDecimal getTotalAmount() {
        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<Long, Integer> entry : cart.entrySet()) {
            Long productId = entry.getKey();
            Integer quantity = entry.getValue();
            Product product = products.get(productId);

            if (product != null) {
                BigDecimal price = product.getSalePrice() != null && product.getSalePrice().compareTo(BigDecimal.ZERO) > 0
                    ? product.getSalePrice()
                    : product.getPrice();

                total = total.add(price.multiply(BigDecimal.valueOf(quantity)));
            }
        }

        return total;
    }

    public boolean isEmpty() {
        return cart.isEmpty();
    }

    public int getItemCount(Long productId) {
        return cart.getOrDefault(productId, 0);
    }
}
