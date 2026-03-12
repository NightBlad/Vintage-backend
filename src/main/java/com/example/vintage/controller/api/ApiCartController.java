package com.example.vintage.controller.api;

import com.example.vintage.entity.Product;
import com.example.vintage.repository.ProductRepository;
import com.example.vintage.service.CartService;
import com.example.vintage.service.SessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/cart", "/api/v1/cart"})
public class ApiCartController {

    private final CartService cartService;
    private final ProductRepository productRepository;
    private final SessionService sessionService;

    public ApiCartController(CartService cartService,
                             ProductRepository productRepository,
                             SessionService sessionService) {
        this.cartService = cartService;
        this.productRepository = productRepository;
        this.sessionService = sessionService;
    }

    /**
     * GET /api/cart
     * Lấy danh sách sản phẩm trong giỏ hàng
     */
    @GetMapping
    public ResponseEntity<?> getCart() {
        Map<Product, Integer> cartItems = cartService.getCartItems();
        List<Map<String, Object>> items = new ArrayList<>();

        for (Map.Entry<Product, Integer> entry : cartItems.entrySet()) {
            Product p = entry.getKey();
            int qty = entry.getValue();
            BigDecimal price = p.getSalePrice() != null && p.getSalePrice().compareTo(BigDecimal.ZERO) > 0
                    ? p.getSalePrice() : p.getPrice();

            Map<String, Object> item = new java.util.HashMap<>();
            item.put("productId", p.getId());
            item.put("productName", p.getName());
            item.put("imageUrl", p.getImageUrl() != null ? "/uploads/" + p.getImageUrl() : null);
            item.put("price", price);
            item.put("originalPrice", p.getPrice());
            item.put("quantity", qty);
            item.put("subtotal", price.multiply(BigDecimal.valueOf(qty)));
            item.put("stockQuantity", p.getStockQuantity());
            items.add(item);
        }

        return ResponseEntity.ok(Map.of(
                "items", items,
                "totalItems", cartService.getTotalItems(),
                "totalAmount", cartService.getTotalAmount()
        ));
    }

    /**
     * POST /api/cart/add
     * Body: { "productId": 1, "quantity": 2 }
     */
    @PostMapping("/add")
    public ResponseEntity<?> addToCart(@RequestBody Map<String, Object> body) {
        Long productId = Long.valueOf(body.get("productId").toString());
        int quantity = body.containsKey("quantity") ? Integer.parseInt(body.get("quantity").toString()) : 1;

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || !product.isActive()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sản phẩm không tồn tại hoặc không khả dụng"));
        }
        if (product.getStockQuantity() < quantity) {
            return ResponseEntity.badRequest().body(Map.of("error", "Số lượng sản phẩm không đủ"));
        }

        cartService.addToCart(product, quantity);
        return ResponseEntity.ok(Map.of(
                "message", "Đã thêm sản phẩm vào giỏ hàng",
                "totalItems", cartService.getTotalItems()
        ));
    }

    /**
     * PUT /api/cart/update
     * Body: { "productId": 1, "quantity": 3 }
     */
    @PutMapping("/update")
    public ResponseEntity<?> updateCart(@RequestBody Map<String, Object> body) {
        Long productId = Long.valueOf(body.get("productId").toString());
        int quantity = Integer.parseInt(body.get("quantity").toString());

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sản phẩm không tồn tại"));
        }
        if (quantity > product.getStockQuantity()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Số lượng sản phẩm không đủ"));
        }

        if (quantity <= 0) {
            cartService.removeFromCart(product);
            return ResponseEntity.ok(Map.of("message", "Đã xóa sản phẩm khỏi giỏ hàng"));
        }

        cartService.updateCart(product, quantity);
        return ResponseEntity.ok(Map.of(
                "message", "Đã cập nhật giỏ hàng",
                "totalItems", cartService.getTotalItems(),
                "totalAmount", cartService.getTotalAmount()
        ));
    }

    /**
     * DELETE /api/cart/remove/{productId}
     */
    @DeleteMapping("/remove/{productId}")
    public ResponseEntity<?> removeFromCart(@PathVariable Long productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sản phẩm không tồn tại"));
        }
        cartService.removeFromCart(product);
        return ResponseEntity.ok(Map.of(
                "message", "Đã xóa sản phẩm khỏi giỏ hàng",
                "totalItems", cartService.getTotalItems()
        ));
    }

    /**
     * DELETE /api/cart/clear
     */
    @DeleteMapping("/clear")
    public ResponseEntity<?> clearCart() {
        cartService.clearCart();
        return ResponseEntity.ok(Map.of("message", "Đã xóa toàn bộ giỏ hàng"));
    }

    /**
     * GET /api/cart/count
     */
    @GetMapping("/count")
    public ResponseEntity<?> getCartCount() {
        return ResponseEntity.ok(Map.of("totalItems", cartService.getTotalItems()));
    }
}

