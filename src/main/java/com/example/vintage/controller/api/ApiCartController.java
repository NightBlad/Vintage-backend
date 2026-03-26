package com.example.vintage.controller.api;

import com.example.vintage.entity.Order;
import com.example.vintage.entity.Product;
import com.example.vintage.entity.User;
import com.example.vintage.repository.ProductRepository;
import com.example.vintage.service.CartService;
import com.example.vintage.service.InventoryService;
import com.example.vintage.service.OrderService;
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
    private final InventoryService inventoryService;
    private final OrderService orderService;

    public ApiCartController(CartService cartService,
                             ProductRepository productRepository,
                             SessionService sessionService,
                             InventoryService inventoryService,
                             OrderService orderService) {
        this.cartService = cartService;
        this.productRepository = productRepository;
        this.sessionService = sessionService;
        this.inventoryService = inventoryService;
        this.orderService = orderService;
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
            item.put("stockQuantity", inventoryService.getAvailableQuantity(p));
            items.add(item);
        }

        BigDecimal productTotal = cartService.getTotalAmount();
        BigDecimal shippingFee = orderService.calculateShippingFee(productTotal);
        BigDecimal grandTotal = productTotal.add(shippingFee);

        return ResponseEntity.ok(Map.of(
                "items", items,
                "totalItems", cartService.getTotalItems(),
                "totalAmount", productTotal,
                "shippingFee", shippingFee,
                "freeShippingThreshold", OrderService.FREE_SHIPPING_THRESHOLD,
                "grandTotal", grandTotal
        ));
    }

    /**
     * POST /api/cart/add
     * Body: { "productId": 1, "quantity": 2 }
     */
    @PostMapping("/add")
    public ResponseEntity<?> addToCart(@RequestBody Map<String, Object> body) {
        if (body == null || !body.containsKey("productId")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Thiếu productId"));
        }

        Long productId;
        int quantity;
        try {
            productId = Long.valueOf(body.get("productId").toString().trim());
            quantity = body.containsKey("quantity") ? Integer.parseInt(body.get("quantity").toString().trim()) : 1;
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Dữ liệu productId/quantity không hợp lệ"));
        }

        if (quantity <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Số lượng phải lớn hơn 0"));
        }

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || !product.isActive()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sản phẩm không tồn tại hoặc không khả dụng"));
        }

        int currentInCart = cartService.getItemCount(productId);
        int requestedTotal = currentInCart + quantity;

        int availableStock = inventoryService.getAvailableQuantity(product);
        if (availableStock < requestedTotal) {
            return ResponseEntity.badRequest().body(Map.of("error", "Số lượng sản phẩm trong kho không đủ"));
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
        if (body == null || !body.containsKey("productId") || !body.containsKey("quantity")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Thiếu productId hoặc quantity"));
        }

        Long productId;
        int quantity;
        try {
            productId = Long.valueOf(body.get("productId").toString().trim());
            quantity = Integer.parseInt(body.get("quantity").toString().trim());
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Dữ liệu productId/quantity không hợp lệ"));
        }

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sản phẩm không tồn tại"));
        }

        int availableStock = inventoryService.getAvailableQuantity(product);
        if (quantity > availableStock) {
            return ResponseEntity.badRequest().body(Map.of("error", "Số lượng sản phẩm trong kho không đủ"));
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

    /**
     * POST /api/cart/place-order
     * Đặt hàng từ giỏ hàng hiện tại
     * Body: { "fullName": "...", "phone": "...", "address": "...", "notes": "...", "paymentMethod": "COD" }
     */
    @PostMapping("/place-order")
    public ResponseEntity<?> placeOrder(@RequestBody Map<String, String> body) {
        User currentUser = sessionService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Vui lòng đăng nhập để đặt hàng"));
        }

        Map<Product, Integer> cartItems = cartService.getCartItems();
        if (cartItems.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Giỏ hàng trống"));
        }

        String fullName = body.get("fullName");
        String phone = body.get("phone");
        String address = body.get("address");
        String notes = body.getOrDefault("notes", "");
        String paymentMethod = body.getOrDefault("paymentMethod", "COD");

        if (fullName == null || fullName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Họ tên không được để trống"));
        }
        if (phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Số điện thoại không được để trống"));
        }
        if (address == null || address.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Địa chỉ không được để trống"));
        }

        try {
            Order order = orderService.createOrder(cartItems, fullName, phone, address, notes, paymentMethod, currentUser);
            cartService.clearCart();
            return ResponseEntity.ok(Map.of(
                    "message", "Đặt hàng thành công!",
                    "orderId", order.getId(),
                    "orderNumber", order.getOrderNumber(),
                    "totalAmount", order.getTotalAmount(),
                    "shippingFee", order.getShippingFee(),
                    "paymentMethod", order.getPaymentMethod().name(),
                    "paymentStatus", order.getPaymentStatus().name()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
