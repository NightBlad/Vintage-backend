package com.example.vintage.service;

import com.example.vintage.entity.*;
import com.example.vintage.repository.OrderRepository;
import com.example.vintage.repository.UserRepository;
import com.example.vintage.repository.ProductRepository;
import com.example.vintage.service.InventoryService;
import com.example.vintage.repository.WarehouseRepository;
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
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final WarehouseRepository warehouseRepository;

    public OrderService(OrderRepository orderRepository,
                       UserRepository userRepository,
                       ProductRepository productRepository,
                       InventoryService inventoryService,
                       WarehouseRepository warehouseRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.warehouseRepository = warehouseRepository;
    }

    public List<Order> findOrdersByUser(User user) {
        // Thử cả hai cách: theo User object và theo User ID
        List<Order> ordersByUser = orderRepository.findByUserOrderByOrderDateDesc(user);
        List<Order> ordersByUserId = orderRepository.findByUserIdOrderByOrderDateDesc(user.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent();

        System.out.println("Orders found by User object: " + ordersByUser.size());
        System.out.println("Orders found by User ID: " + ordersByUserId.size());

        // Trả về kết quả có nhiều đơn hàng hơn
        return ordersByUserId.size() > ordersByUser.size() ? ordersByUserId : ordersByUser;
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
