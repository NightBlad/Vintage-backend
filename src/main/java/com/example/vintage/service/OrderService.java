package com.example.vintage.service;

import com.example.vintage.entity.*;
import com.example.vintage.repository.OrderRepository;
import com.example.vintage.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.List;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    public OrderService(OrderRepository orderRepository,
                       ProductRepository productRepository,
                       InventoryService inventoryService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
    }

    public List<Order> findOrdersByUser(User user) {
        return orderRepository.findByUserOrderByOrderDateDesc(user);
    }

    public Order createOrder(Map<Product, Integer> cartItems,
                           String fullName,
                           String phone,
                           String address,
                           String notes,
                           String paymentMethodStr,
                           User currentUser) {

        // Tạo đơn hàng mới
        Order order = new Order();
        order.setUser(currentUser);
        order.setCustomerName(fullName);  // Sử dụng customerName thay vì fullName
        order.setCustomerPhone(phone);    // Sử dụng customerPhone thay vì phone
        order.setShippingAddress(address); // Sử dụng shippingAddress thay vì address
        order.setNotes(notes);
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING);

        // Tạo mã đơn hàng
        order.setOrderNumber(generateOrderNumber());

        // Chuyển đổi payment method string thành enum
        PaymentMethod paymentMethod;
        switch (paymentMethodStr.toUpperCase()) {
            case "BANK_TRANSFER":
                paymentMethod = PaymentMethod.BANK_TRANSFER;
                break;
            case "MOMO":
                paymentMethod = PaymentMethod.E_WALLET;  // Sử dụng E_WALLET thay vì MOMO
                break;
            case "CREDIT_CARD":
                paymentMethod = PaymentMethod.CREDIT_CARD;
                break;
            default:
                paymentMethod = PaymentMethod.COD;
                break;
        }
        order.setPaymentMethod(paymentMethod);
        order.setPaymentStatus(PaymentStatus.UNPAID);

        // Tính tổng tiền và tạo order items
        BigDecimal totalAmount = BigDecimal.ZERO;
        Set<OrderItem> orderItems = new HashSet<>();

        // Check stock in inventory module BEFORE creating items
        for (Map.Entry<Product, Integer> entry : cartItems.entrySet()) {
            Product product = productRepository.findById(entry.getKey().getId())
                    .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại: " + entry.getKey().getId()));
            if (!product.isActive()) {
                throw new RuntimeException("Sản phẩm " + product.getName() + " hiện không khả dụng");
            }
            int available = inventoryService.getAvailableQuantity(product);
            if (available < entry.getValue()) {
                throw new RuntimeException("Sản phẩm " + product.getName() + " đã hết hàng");
            }
        }

        // Sau khi pass check stock, mới build Order & OrderItems như cũ
        for (Map.Entry<Product, Integer> entry : cartItems.entrySet()) {
            Product product = entry.getKey();
            Integer quantity = entry.getValue();

            // Tạo order item
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(quantity);

            // Sử dụng giá sale nếu có, không thì dùng giá gốc
            BigDecimal unitPrice = (product.getSalePrice() != null && product.getSalePrice().compareTo(BigDecimal.ZERO) > 0)
                                 ? product.getSalePrice()
                                 : product.getPrice();
            orderItem.setPrice(unitPrice);  // Sử dụng setPrice thay vì setUnitPrice

            BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            orderItem.setTotalPrice(itemTotal);

            orderItems.add(orderItem);
            totalAmount = totalAmount.add(itemTotal);
        }

        // Tính phí vận chuyển
        BigDecimal shippingFee = calculateShippingFee(totalAmount);
        order.setShippingFee(shippingFee);
        order.setTotalAmount(totalAmount.add(shippingFee));
        order.setOrderItems(orderItems);

        // Lưu đơn hàng
        return orderRepository.save(order);
    }

    private BigDecimal calculateShippingFee(BigDecimal totalAmount) {
        // Miễn phí ship cho đơn hàng trên 500,000 VND
        if (totalAmount.compareTo(BigDecimal.valueOf(500000)) >= 0) {
            return BigDecimal.ZERO;
        }
        // Phí ship cố định 30,000 VND
        return BigDecimal.valueOf(30000);
    }

    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));
    }

    public void updateOrderStatus(Long orderId, OrderStatus status) {
        Order order = findById(orderId);
        OrderStatus oldStatus = order.getStatus();
        order.setStatus(status);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        // Kho chỉ có 1, dùng kho mặc định
        Warehouse warehouse = inventoryService.getDefaultWarehouse();

        // Khi chuyển sang CONFIRMED: trừ kho
        if (oldStatus == OrderStatus.PENDING && status == OrderStatus.CONFIRMED) {
            order.getOrderItems().forEach(item ->
                    inventoryService.exportStock(
                            item.getProduct(),
                            warehouse,
                            item.getQuantity(),
                            order.getOrderNumber(),
                            "Xuất kho cho đơn hàng " + order.getOrderNumber()
                    )
            );
        }

        // Khi chuyển sang CANCELED: hoàn kho nếu trước đó đã CONFIRMED
        if (oldStatus == OrderStatus.CONFIRMED && status == OrderStatus.CANCELLED) {
            order.getOrderItems().forEach(item ->
                    inventoryService.importStock(
                            item.getProduct(),
                            warehouse,
                            item.getQuantity(),
                            order.getOrderNumber(),
                            "Hoàn kho do hủy đơn " + order.getOrderNumber()
                    )
            );
        }
    }

    /**
     * Xử lý thanh toán cho đơn hàng.
     * - COD: chỉ được xác nhận thanh toán khi đơn đã DELIVERED
     * - BANK_TRANSFER / CREDIT_CARD / E_WALLET: cần transactionRef, đơn phải ở trạng thái PENDING hoặc CONFIRMED
     */
    public Order processPayment(Long orderId, String transactionRef, User currentUser) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        if (!order.getUser().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Bạn không có quyền thanh toán đơn hàng này");
        }

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new RuntimeException("Đơn hàng đã được thanh toán rồi");
        }

        if (order.getPaymentStatus() == PaymentStatus.REFUNDED) {
            throw new RuntimeException("Đơn hàng đã được hoàn tiền, không thể thanh toán lại");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new RuntimeException("Không thể thanh toán cho đơn hàng đã bị hủy");
        }

        PaymentMethod method = order.getPaymentMethod();

        if (method == PaymentMethod.COD) {
            if (order.getStatus() != OrderStatus.DELIVERED) {
                throw new RuntimeException("Thanh toán COD chỉ được xác nhận khi đơn hàng đã giao thành công");
            }
        } else {
            if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
                throw new RuntimeException("Chỉ có thể thanh toán khi đơn hàng đang chờ xử lý hoặc đã xác nhận");
            }
            if (transactionRef == null || transactionRef.isBlank()) {
                throw new RuntimeException("Vui lòng cung cấp mã giao dịch để xác nhận thanh toán");
            }
        }

        order.setPaymentStatus(PaymentStatus.PAID);
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    /**
     * Admin xác nhận thanh toán cho đơn hàng (không cần kiểm tra quyền sở hữu).
     */
    public Order confirmPayment(Long orderId, String transactionRef) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new RuntimeException("Đơn hàng đã được thanh toán rồi");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new RuntimeException("Không thể xác nhận thanh toán cho đơn hàng đã hủy");
        }

        order.setPaymentStatus(PaymentStatus.PAID);
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    public List<Order> findAllOrders() {
        return orderRepository.findAll();
    }

    private String generateOrderNumber() {
        // Tạo mã đơn hàng theo format: VTG + timestamp + random
        String timestamp = String.valueOf(System.currentTimeMillis());
        String random = String.valueOf((int)(Math.random() * 1000));
        return "VTG" + timestamp.substring(timestamp.length() - 8) + String.format("%03d", Integer.parseInt(random));
    }
}
