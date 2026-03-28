package com.example.vintage.repository;

import com.example.vintage.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    // no extra methods for now
}

