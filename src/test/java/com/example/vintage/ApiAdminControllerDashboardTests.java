package com.example.vintage;

import com.example.vintage.controller.api.ApiAdminController;
import com.example.vintage.entity.Order;
import com.example.vintage.entity.OrderItem;
import com.example.vintage.entity.OrderStatus;
import com.example.vintage.entity.Product;
import com.example.vintage.repository.CategoryRepository;
import com.example.vintage.repository.OrderRepository;
import com.example.vintage.repository.ProductRepository;
import com.example.vintage.repository.RoleRepository;
import com.example.vintage.repository.UserRepository;
import com.example.vintage.service.FileUploadService;
import com.example.vintage.service.InventoryService;
import com.example.vintage.service.OrderService;
import com.example.vintage.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ApiAdminControllerDashboardTests {

    private ProductRepository productRepository;
    private CategoryRepository categoryRepository;
    private OrderRepository orderRepository;
    private UserRepository userRepository;
    private ApiAdminController apiAdminController;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        orderRepository = mock(OrderRepository.class);
        userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        FileUploadService fileUploadService = mock(FileUploadService.class);
        ProductService productService = mock(ProductService.class);
        InventoryService inventoryService = mock(InventoryService.class);
        OrderService orderService = mock(OrderService.class);

        apiAdminController = new ApiAdminController(
                productRepository,
                categoryRepository,
                orderRepository,
                userRepository,
                roleRepository,
                fileUploadService,
                productService,
                inventoryService,
                orderService
        );
    }

    @Test
    void dashboard_ShouldReturnContractFieldsAndSalesSummary() {
        Product lowStock = new Product();
        lowStock.setId(1L);
        lowStock.setName("Vitamin C");
        lowStock.setProductCode("VTM-C");
        lowStock.setPrice(BigDecimal.valueOf(100000));
        lowStock.setSalePrice(BigDecimal.valueOf(90000));
        lowStock.setStockQuantity(3);

        Order delivered = buildOrder(100L, OrderStatus.DELIVERED, BigDecimal.valueOf(200000), 2, lowStock);
        Order cancelled = buildOrder(101L, OrderStatus.CANCELLED, BigDecimal.valueOf(120000), 1, lowStock);
        Order shipped = buildOrder(102L, OrderStatus.SHIPPED, BigDecimal.valueOf(150000), 1, lowStock);

        when(productRepository.count()).thenReturn(20L);
        when(categoryRepository.count()).thenReturn(5L);
        when(userRepository.count()).thenReturn(7L);
        when(orderRepository.count()).thenReturn(33L);
        when(productRepository.findByStockQuantityLessThan(10)).thenReturn(List.of(lowStock));
        when(orderRepository.findAllOrderByOrderDateDesc(PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(delivered, cancelled, shipped)));

        ResponseEntity<?> response = apiAdminController.dashboard();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> body = (Map<?, ?>) response.getBody();

        assertThat(body.get("totalProducts")).isEqualTo(20L);
        assertThat(body.get("totalCategories")).isEqualTo(5L);
        assertThat(body.get("totalUsers")).isEqualTo(7L);
        assertThat(body.get("totalOrders")).isEqualTo(33L);
        assertThat(body.get("lowStockCount")).isEqualTo(1);
        assertThat(body.get("lowStockProducts")).isInstanceOf(List.class);
        assertThat(body.get("recentOrders")).isInstanceOf(List.class);

        Map<?, ?> salesSummary = (Map<?, ?>) body.get("salesSummary");
        assertThat(salesSummary.get("recentOrderCount")).isEqualTo(3);
        assertThat(salesSummary.get("recentRevenue")).isEqualTo(BigDecimal.valueOf(200000));
        assertThat(salesSummary.get("recentAov")).isEqualTo(BigDecimal.valueOf(200000).setScale(2));
        assertThat(salesSummary.get("recentCancellationRate")).isEqualTo(33.33d);

        List<Map<?, ?>> statusStats = (List<Map<?, ?>>) salesSummary.get("statusStats");
        assertThat(statusStats).isNotEmpty();
        assertThat(statusStats.stream().anyMatch(s -> "SHIPPING".equals(s.get("status")))).isTrue();
    }

    private Order buildOrder(Long id, OrderStatus status, BigDecimal totalAmount, int qty, Product product) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNumber("ORD-" + id);
        order.setStatus(status);
        order.setTotalAmount(totalAmount);
        order.setShippingFee(BigDecimal.ZERO);
        order.setCustomerName("Customer");
        order.setCustomerPhone("0123456789");
        order.setShippingAddress("HN");
        order.setOrderDate(LocalDateTime.now());

        OrderItem item = new OrderItem();
        item.setId(id + 1000);
        item.setProduct(product);
        item.setQuantity(qty);
        item.setPrice(BigDecimal.valueOf(100000));
        item.setTotalPrice(BigDecimal.valueOf(100000L * qty));

        order.setOrderItems(Set.of(item));
        return order;
    }
}

