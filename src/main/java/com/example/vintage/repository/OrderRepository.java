package com.example.vintage.repository;

import com.example.vintage.entity.Order;
import com.example.vintage.entity.OrderStatus;
import com.example.vintage.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    Page<Order> findByUserIdOrderByOrderDateDesc(Long userId, Pageable pageable);

    List<Order> findByUserOrderByOrderDateDesc(User user);

    Page<Order> findByStatusOrderByOrderDateDesc(OrderStatus status, Pageable pageable);

    @Query("SELECT o FROM Order o ORDER BY o.orderDate DESC")
    Page<Order> findAllOrderByOrderDateDesc(Pageable pageable);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.orderItems oi LEFT JOIN FETCH oi.product WHERE o.id = :id")
    Optional<Order> findByIdWithItems(Long id);

    @Query("SELECT o FROM Order o WHERE o.orderDate BETWEEN ?1 AND ?2 ORDER BY o.orderDate DESC")
    List<Order> findOrdersBetweenDates(LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = ?1")
    Long countByStatus(OrderStatus status);

    @Query("SELECT o FROM Order o WHERE " +
            "(LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(o.customerName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(o.customerPhone) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "ORDER BY o.orderDate DESC")
    Page<Order> search(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.status = :status AND " +
            "(LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(o.customerName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(o.customerPhone) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "ORDER BY o.orderDate DESC")
    Page<Order> searchByStatus(@Param("status") OrderStatus status, @Param("keyword") String keyword, Pageable pageable);
}
