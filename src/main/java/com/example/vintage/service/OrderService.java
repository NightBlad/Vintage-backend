package com.example.vintage.service;

import com.example.vintage.entity.*;
import com.example.vintage.repository.OrderRepository;
import com.example.vintage.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository,
                       UserRepository userRepository,
                       ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
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

        for (Map.Entry<Product, Integer> entry : cartItems.entrySet()) {
            Product product = entry.getKey();
            Integer quantity = entry.getValue();

            Product managedProduct = productRepository.findById(product.getId())
                    .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại: " + product.getId()));

            if (!managedProduct.isActive()) {
                throw new RuntimeException("Sản phẩm " + managedProduct.getName() + " hiện không khả dụng");
            }

            // Kiểm tra số lượng trong kho
            if (managedProduct.getStockQuantity() < quantity) {
                throw new RuntimeException("Sản phẩm " + managedProduct.getName() + " không đủ số lượng trong kho");
            }

            // Tạo order item
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(managedProduct);
            orderItem.setQuantity(quantity);

            // Sử dụng giá sale nếu có, không thì dùng giá gốc
            BigDecimal unitPrice = (managedProduct.getSalePrice() != null && managedProduct.getSalePrice().compareTo(BigDecimal.ZERO) > 0)
                                 ? managedProduct.getSalePrice()
                                 : managedProduct.getPrice();
            orderItem.setPrice(unitPrice);  // Sử dụng setPrice thay vì setUnitPrice

            BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            orderItem.setTotalPrice(itemTotal);

            orderItems.add(orderItem);
            totalAmount = totalAmount.add(itemTotal);

            // Cập nhật số lượng trong kho
            managedProduct.setStockQuantity(managedProduct.getStockQuantity() - quantity);
            productRepository.save(managedProduct);
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
        order.setStatus(status);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
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
