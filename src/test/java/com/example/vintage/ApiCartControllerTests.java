package com.example.vintage;

import com.example.vintage.controller.api.ApiCartController;
import com.example.vintage.entity.Product;
import com.example.vintage.repository.ProductRepository;
import com.example.vintage.service.CartService;
import com.example.vintage.service.InventoryService;
import com.example.vintage.service.OrderService;
import com.example.vintage.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ApiCartControllerTests {

    private CartService cartService;
    private ProductRepository productRepository;
    private SessionService sessionService;
    private InventoryService inventoryService;
    private OrderService orderService;
    private ApiCartController apiCartController;

    @BeforeEach
    void setUp() {
        cartService = mock(CartService.class);
        productRepository = mock(ProductRepository.class);
        sessionService = mock(SessionService.class);
        inventoryService = mock(InventoryService.class);
        orderService = mock(OrderService.class);

        apiCartController = new ApiCartController(cartService, productRepository, sessionService, inventoryService, orderService);
    }

    @Test // Test thêm sản phẩm vào giỏ thành công
    void testAddProductToCart_ShouldSuccess() {
        Product product = buildProduct(1L, 10, true);
        when(productRepository.findById(1L)).thenReturn(java.util.Optional.of(product));
        when(cartService.getItemCount(1L)).thenReturn(0);
        when(inventoryService.getAvailableQuantity(product)).thenReturn(10);

        Map<String, Object> body = Map.of(
                "productId", 1L,
                "quantity", 2
        );

        when(cartService.getTotalItems()).thenReturn(2);

        ResponseEntity<?> response = apiCartController.addToCart(body);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> res = (Map<?, ?>) response.getBody();
        assertThat(res.get("message")).isEqualTo("Đã thêm sản phẩm vào giỏ hàng");
        assertThat(res.get("totalItems")).isEqualTo(2);
        verify(cartService).addToCart(product, 2);
    }

    @Test // Test thêm sản phẩm vào giỏ khi số lượng vượt quá tồn kho -> phải fail
    void testIncreaseQuantity_ExceedStock_ShouldFail() {
        Product product = buildProduct(1L, 3, true); // stock = 3
        when(productRepository.findById(1L)).thenReturn(java.util.Optional.of(product));
        when(cartService.getItemCount(1L)).thenReturn(0);
        when(inventoryService.getAvailableQuantity(product)).thenReturn(3);

        Map<String, Object> body = Map.of(
                "productId", 1L,
                "quantity", 5 // lớn hơn stock
        );

        ResponseEntity<?> response = apiCartController.addToCart(body);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        Map<?, ?> res = (Map<?, ?>) response.getBody();
        assertThat(res.get("error")).isEqualTo("Số lượng sản phẩm trong kho không đủ");
        verify(cartService, never()).addToCart(any(), anyInt());
    }

    @Test // Test cập nhật tăng số lượng trong giỏ thành công
    void testIncreaseCartQuantity_ShouldUpdateQuantity() {
        Product product = buildProduct(1L, 10, true);
        when(productRepository.findById(1L)).thenReturn(java.util.Optional.of(product));
        when(inventoryService.getAvailableQuantity(product)).thenReturn(10);

        Map<String, Object> body = Map.of(
                "productId", 1L,
                "quantity", 5
        );

        when(cartService.getTotalItems()).thenReturn(5);
        when(cartService.getTotalAmount()).thenReturn(BigDecimal.valueOf(500_000));

        ResponseEntity<?> response = apiCartController.updateCart(body);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> res = (Map<?, ?>) response.getBody();
        assertThat(res.get("message")).isEqualTo("Đã cập nhật giỏ hàng");
        assertThat(res.get("totalItems")).isEqualTo(5);
        assertThat(res.get("totalAmount")).isEqualTo(BigDecimal.valueOf(500_000));
        verify(cartService).updateCart(product, 5);
    }

    @Test // Test remove sản phẩm khỏi giỏ thành công
    void testRemoveProductFromCart_ShouldSuccess() {
        Product product = buildProduct(1L, 10, true);
        when(productRepository.findById(1L)).thenReturn(java.util.Optional.of(product));
        when(cartService.getTotalItems()).thenReturn(0);

        ResponseEntity<?> response = apiCartController.removeFromCart(1L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> res = (Map<?, ?>) response.getBody();
        assertThat(res.get("message")).isEqualTo("Đã xóa sản phẩm khỏi giỏ hàng");
        assertThat(res.get("totalItems")).isEqualTo(0);
        verify(cartService).removeFromCart(product);
    }

    // Lưu ý: ApiCartController hiện tại không check đăng nhập nên
    // 2 test "khi chưa đăng nhập" sẽ chỉ có thể viết được
    // khi controller bổ sung logic dùng sessionService để kiểm tra.

    // Hàm tiện ích tạo Product
    private Product buildProduct(Long id, int stock, boolean active) {
        Product p = new Product();
        p.setId(id);
        p.setName("P" + id);
        p.setPrice(BigDecimal.valueOf(100_000));
        p.setSalePrice(BigDecimal.valueOf(90_000));
        p.setStockQuantity(stock);
        p.setActive(active);
        return p;
    }
}
