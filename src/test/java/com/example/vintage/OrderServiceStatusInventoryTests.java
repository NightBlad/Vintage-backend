package com.example.vintage;

import com.example.vintage.entity.Order;
import com.example.vintage.entity.OrderItem;
import com.example.vintage.entity.OrderStatus;
import com.example.vintage.entity.Product;
import com.example.vintage.entity.Warehouse;
import com.example.vintage.repository.OrderRepository;
import com.example.vintage.repository.ProductRepository;
import com.example.vintage.service.InventoryService;
import com.example.vintage.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceStatusInventoryTests {

    private OrderRepository orderRepository;
    private ProductRepository productRepository;
    private InventoryService inventoryService;
    private OrderService orderService;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        productRepository = mock(ProductRepository.class);
        inventoryService = mock(InventoryService.class);
        orderService = new OrderService(orderRepository, productRepository, inventoryService);
        warehouse = new Warehouse();
        warehouse.setId(1L);
        when(inventoryService.getDefaultWarehouse()).thenReturn(warehouse);
    }

    @Test
    void updateStatus_toConfirmed_shouldExportStockOnce() {
        Order order = buildOrder(OrderStatus.PENDING, false, 2);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.updateOrderStatus(1L, OrderStatus.CONFIRMED);

        OrderItem item = order.getOrderItems().iterator().next();
        verify(inventoryService, times(1)).exportStock(
                eq(item.getProduct()),
                eq(warehouse),
                eq(item.getQuantity()),
                eq(order.getOrderNumber()),
                contains("Xuất kho")
        );
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.isStockDeducted()).isTrue();
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void updateStatus_toProcessing_shouldNotExportAgainWhenAlreadyDeducted() {
        Order order = buildOrder(OrderStatus.CONFIRMED, true, 3);
        when(orderRepository.findById(2L)).thenReturn(Optional.of(order));

        orderService.updateOrderStatus(2L, OrderStatus.PROCESSING);

        verify(inventoryService, never()).exportStock(any(), any(), anyInt(), anyString(), anyString());
        verify(inventoryService, never()).importStock(any(), any(), anyInt(), anyString(), anyString());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(order.isStockDeducted()).isTrue();
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void updateStatus_toShipped_shouldKeepSingleExport() {
        Order order = buildOrder(OrderStatus.PROCESSING, true, 1);
        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));

        orderService.updateOrderStatus(3L, OrderStatus.SHIPPED);

        verify(inventoryService, never()).exportStock(any(), any(), anyInt(), anyString(), anyString());
        verify(inventoryService, never()).importStock(any(), any(), anyInt(), anyString(), anyString());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.isStockDeducted()).isTrue();
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void updateStatus_toCancelled_shouldRestoreStockWhenDeducted() {
        Order order = buildOrder(OrderStatus.SHIPPED, true, 4);
        when(orderRepository.findById(4L)).thenReturn(Optional.of(order));

        orderService.updateOrderStatus(4L, OrderStatus.CANCELLED);

        OrderItem item = order.getOrderItems().iterator().next();
        verify(inventoryService, times(1)).importStock(
                eq(item.getProduct()),
                eq(warehouse),
                eq(item.getQuantity()),
                eq(order.getOrderNumber()),
                contains("Hoàn kho")
        );
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.isStockDeducted()).isFalse();
        verify(orderRepository, times(1)).save(order);
    }

    private Order buildOrder(OrderStatus status, boolean stockDeducted, int quantity) {
        Product product = new Product();
        product.setId(10L);
        product.setName("Test");

        OrderItem item = new OrderItem();
        item.setId(100L);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setPrice(BigDecimal.valueOf(50000));
        item.setTotalPrice(BigDecimal.valueOf(50000L * quantity));

        Order order = new Order();
        order.setId(1L);
        order.setOrderNumber("VTG-ORDER-" + status.name());
        order.setStatus(status);
        order.setStockDeducted(stockDeducted);
        order.setTotalAmount(BigDecimal.valueOf(100000));
        item.setOrder(order);
        order.setOrderItems(Set.of(item));
        return order;
    }
}
