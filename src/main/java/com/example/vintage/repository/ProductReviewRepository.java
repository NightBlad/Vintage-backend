package com.example.vintage.repository;

import com.example.vintage.entity.ProductReview;
import com.example.vintage.entity.Product;
import com.example.vintage.entity.User;
import com.example.vintage.entity.Order;
import com.example.vintage.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    List<ProductReview> findByProduct(Product product);

    boolean existsByProductAndOrderAndOrderItemAndUser(Product product, Order order, OrderItem orderItem, User user);

    Optional<ProductReview> findByOrderItemAndUser(OrderItem orderItem, User user);
}

