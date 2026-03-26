package com.example.vintage.entity;

public enum RoleName {
    ROLE_ADMIN,    // Quản trị viên - có quyền quản lý toàn bộ hệ thống
    ROLE_STAFF,    // Nhân viên - quản lý nghiệp vụ nhưng không quản lý tài khoản người dùng
    ROLE_USER      // Người dùng thường - có thể mua hàng và xem sản phẩm
}
