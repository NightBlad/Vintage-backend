package com.example.vintage.dto;

public class ProductReviewDTO {
    private Long id;
    private Long productId;
    private Long orderId;
    private Long orderItemId;
    private Long userId;
    private int rating;
    private String comment;

    public ProductReviewDTO() {
    }

    public ProductReviewDTO(Long id, Long productId, Long orderId, Long orderItemId, Long userId, int rating, String comment) {
        this.id = id;
        this.productId = productId;
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.userId = userId;
        this.rating = rating;
        this.comment = comment;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(Long orderItemId) {
        this.orderItemId = orderItemId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
