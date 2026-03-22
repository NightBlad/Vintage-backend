package com.example.vintage.controller.api;

import com.example.vintage.entity.Order;
import com.example.vintage.entity.OrderStatus;
import com.example.vintage.entity.Product;
import com.example.vintage.entity.User;
import com.example.vintage.repository.OrderRepository;
import com.example.vintage.service.CartService;
import com.example.vintage.service.OrderService;
import com.example.vintage.service.SessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/orders", "/api/v1/orders"})
public class ApiOrderController {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final SessionService sessionService;

    public ApiOrderController(OrderService orderService,
                              OrderRepository orderRepository,
                              CartService cartService,
                              SessionService sessionService) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.sessionService = sessionService;
    }

    /**
     * GET /api/orders
     * Lấy danh sách đơn hàng của user hiện tại
     */
    @GetMapping
    public ResponseEntity<?> getMyOrders() {
        User currentUser = sessionService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Chưa đăng nhập"));
        }
        List<Order> orders = orderService.findOrdersByUser(currentUser);
        return ResponseEntity.ok(orders.stream().map(this::toOrderSummary).collect(Collectors.toList()));
    }

    /**
     * GET /api/orders/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getOrderById(@PathVariable Long id) {
        User currentUser = sessionService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Chưa đăng nhập"));
        }
        return orderRepository.findByIdWithItems(id)
                .map(order -> {
                    // Chỉ cho phép xem đơn hàng của mình (hoặc admin)
                    if (!order.getUser().getId().equals(currentUser.getId()) && !sessionService.isAdmin()) {
                        return ResponseEntity.status(403).<Object>body(Map.of("error", "Không có quyền truy cập"));
                    }
                    return ResponseEntity.ok((Object) toOrderDetail(order));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/orders/checkout
     * Đặt hàng từ giỏ hàng hiện tại
     * Body: { "fullName": "...", "phone": "...", "address": "...", "notes": "...", "paymentMethod": "COD" }
     */
    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestBody Map<String, String> body) {
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
                    "shippingFee", order.getShippingFee()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * PUT /api/orders/{id}/cancel
     * Hủy đơn hàng
     */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable Long id) {
        User currentUser = sessionService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Chưa đăng nhập"));
        }
        return orderRepository.findById(id)
                .map(order -> {
                    if (!order.getUser().getId().equals(currentUser.getId()) && !sessionService.isAdmin()) {
                        return ResponseEntity.status(403).<Object>body(Map.of("error", "Không có quyền thực hiện"));
                    }
                    if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
                        return ResponseEntity.badRequest().<Object>body(Map.of("error", "Chỉ có thể hủy đơn hàng đang chờ xác nhận hoặc đã xác nhận"));
                    }
                    // dùng service để đảm bảo logic hoàn kho khi CANCELED
                    orderService.updateOrderStatus(order.getId(), OrderStatus.CANCELLED);
                    return ResponseEntity.ok((Object) Map.of("message", "Đơn hàng đã được hủy thành công"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * PUT /api/orders/{id}/confirm
     * Xác nhận đơn hàng
     */
    @PutMapping("/{id}/confirm")
    public ResponseEntity<?> confirmOrder(@PathVariable Long id) {
        User currentUser = sessionService.getCurrentUser();
        if (currentUser == null || !sessionService.isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Chỉ admin mới được xác nhận đơn hàng"));
        }
        return orderRepository.findById(id)
                .map(order -> {
                    if (order.getStatus() != OrderStatus.PENDING) {
                        return ResponseEntity.badRequest().<Object>body(Map.of("error", "Chỉ có thể xác nhận đơn hàng đang chờ xử lý"));
                    }
                    orderService.updateOrderStatus(order.getId(), OrderStatus.CONFIRMED);
                    return ResponseEntity.ok((Object) Map.of("message", "Đơn hàng đã được xác nhận"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===== Helper methods =====

    private Map<String, Object> toOrderSummary(Order o) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", o.getId());
        map.put("orderNumber", o.getOrderNumber());
        map.put("status", o.getStatus());
        map.put("paymentMethod", o.getPaymentMethod());
        map.put("paymentStatus", o.getPaymentStatus());
        map.put("totalAmount", o.getTotalAmount());
        map.put("shippingFee", o.getShippingFee());
        map.put("customerName", o.getCustomerName());
        map.put("customerPhone", o.getCustomerPhone());
        map.put("shippingAddress", o.getShippingAddress());
        map.put("orderDate", o.getOrderDate());
        map.put("itemCount", o.getOrderItems().size());
        return map;
    }

    private Map<String, Object> toOrderDetail(Order o) {
        Map<String, Object> map = new java.util.HashMap<>(toOrderSummary(o));
        map.put("notes", o.getNotes());
        map.put("updatedAt", o.getUpdatedAt());
        map.put("items", o.getOrderItems().stream().map(item -> {
            Map<String, Object> i = new java.util.HashMap<>();
            i.put("id", item.getId());
            i.put("quantity", item.getQuantity());
            i.put("price", item.getPrice());
            i.put("totalPrice", item.getTotalPrice());
            if (item.getProduct() != null) {
                i.put("productId", item.getProduct().getId());
                i.put("productName", item.getProduct().getName());
                i.put("productImage", item.getProduct().getImageUrl() != null
                        ? "/uploads/" + item.getProduct().getImageUrl() : null);
            }
            return i;
        }).collect(Collectors.toList()));
        return map;
    }
}
