package com.example.vintage.service;

import com.example.vintage.dto.ProductReviewDTO;
import com.example.vintage.entity.*;
import com.example.vintage.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductReviewService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductReviewRepository productReviewRepository;

    public ProductReviewService(ProductRepository productRepository,
                                OrderRepository orderRepository,
                                OrderItemRepository orderItemRepository,
                                UserRepository userRepository,
                                ProductReviewRepository productReviewRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.productReviewRepository = productReviewRepository;
    }

    @Transactional
    public ProductReviewDTO createReview(ProductReviewDTO dto) {
        if (dto.getRating() < 1 || dto.getRating() > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("Unauthenticated");
        }

        String username = auth.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        Order order = orderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        OrderItem orderItem = orderItemRepository.findById(dto.getOrderItemId())
                .orElseThrow(() -> new RuntimeException("Order item not found"));

        // Optional: check order belongs to user & item belongs to order
        if (!order.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Order does not belong to current user");
        }
        if (!orderItem.getOrder().getId().equals(order.getId())) {
            throw new RuntimeException("Order item does not belong to order");
        }

        // Optional: allow review only when order delivered
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new RuntimeException("You can only review delivered orders");
        }

        // Prevent duplicate review for same product/order/item/user
        if (productReviewRepository.existsByProductAndOrderAndOrderItemAndUser(product, order, orderItem, user)) {
            throw new RuntimeException("You have already reviewed this product in this order");
        }

        ProductReview review = new ProductReview();
        review.setProduct(product);
        review.setOrder(order);
        review.setOrderItem(orderItem);
        review.setUser(user);
        review.setRating(dto.getRating());
        review.setComment(dto.getComment());

        ProductReview saved = productReviewRepository.save(review);

        ProductReviewDTO result = new ProductReviewDTO();
        result.setId(saved.getId());
        result.setProductId(product.getId());
        result.setOrderId(order.getId());
        result.setOrderItemId(orderItem.getId());
        result.setUserId(user.getId());
        result.setRating(saved.getRating());
        result.setComment(saved.getComment());
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProductReviewsSummary(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        List<ProductReview> reviews = productReviewRepository.findByProduct(product);
        int count = reviews.size();
        double avg = count == 0 ? 0.0 : reviews.stream().mapToInt(ProductReview::getRating).average().orElse(0.0);

        List<Map<String, Object>> reviewDtos = reviews.stream().map(r -> Map.<String, Object>of(
                "id", r.getId(),
                "rating", r.getRating(),
                "comment", r.getComment(),
                "createdAt", r.getCreatedAt(),
                "user", Map.of(
                        "id", r.getUser().getId(),
                        "username", r.getUser().getUsername(),
                        "fullName", r.getUser().getFullName()
                )
        )).collect(Collectors.toList());

        return Map.of(
                "averageRating", avg,
                "reviewCount", count,
                "reviews", reviewDtos
        );
    }
}
