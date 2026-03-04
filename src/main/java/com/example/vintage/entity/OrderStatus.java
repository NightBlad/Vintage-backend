package com.example.vintage.entity;

public enum OrderStatus {
    PENDING,    // Chờ xử lý
    CONFIRMED,  // Đã xác nhận
    PROCESSING, // Đang xử lý
    SHIPPED,    // Đã giao
    DELIVERED,  // Đã nhận
    CANCELLED   // Đã hủy
}
