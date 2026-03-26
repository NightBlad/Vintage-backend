package com.example.vintage;

import com.example.vintage.controller.api.ApiProductController;
import com.example.vintage.entity.Category;
import com.example.vintage.entity.Product;
import com.example.vintage.repository.CategoryRepository;
import com.example.vintage.repository.ProductRepository;
import com.example.vintage.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ApiProductControllerTests {

    private ProductRepository productRepository;
    private CategoryRepository categoryRepository;
    private InventoryService inventoryService;
    private ApiProductController apiProductController;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        inventoryService = mock(InventoryService.class);
        when(inventoryService.getAvailableQuantity(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            return p.getStockQuantity();
        });
        apiProductController = new ApiProductController(productRepository, categoryRepository, inventoryService);
    }

    @Test // Test lấy danh sách sản phẩm trang 1 (page=0) -> trả về đúng số lượng và thông tin phân trang
    void testGetProductList_Page1_ShouldReturnProducts() {
        Pageable pageable = PageRequest.of(0, 2);
        List<Product> products = Arrays.asList(buildProduct(1L, "P1", 10, true), buildProduct(2L, "P2", 5, true));
        Page<Product> page = new PageImpl<>(products, pageable, 4); // tổng 4 sp, đang xem 2 sp đầu

        when(productRepository.findByActiveTrue(pageable)).thenReturn(page);

        ResponseEntity<?> response = apiProductController.getProducts(0, 2, null, null, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        List<?> content = (List<?>) body.get("content");
        assertThat(content).hasSize(2);
        assertThat(body.get("totalElements")).isEqualTo(4L);
        assertThat(body.get("currentPage")).isEqualTo(0);
        assertThat(body.get("hasNext")).isEqualTo(true);
    }

    @Test // Test lấy danh sách sản phẩm trang 2 (page=1) -> trả về trang tiếp theo
    void testGetProductList_Page2_ShouldReturnNextPage() {
        Pageable pageable = PageRequest.of(1, 2);
        List<Product> products = Arrays.asList(buildProduct(3L, "P3", 8, true), buildProduct(4L, "P4", 0, true));
        Page<Product> page = new PageImpl<>(products, pageable, 4); // tổng 4 sp

        when(productRepository.findByActiveTrue(pageable)).thenReturn(page);

        ResponseEntity<?> response = apiProductController.getProducts(1, 2, null, null, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        List<?> content = (List<?>) body.get("content");
        assertThat(content).hasSize(2);
        assertThat(body.get("currentPage")).isEqualTo(1);
        assertThat(body.get("hasPrevious")).isEqualTo(true);
    }

    @Test // Test filter sản phẩm theo categoryId -> chỉ gọi đúng repository method và trả về đúng content
    void testFilterProducts_ByCategory_ShouldReturnCorrectProducts() {
        Pageable pageable = PageRequest.of(0, 10);
        List<Product> products = Collections.singletonList(buildProduct(5L, "CatP1", 3, true));
        Page<Product> page = new PageImpl<>(products, pageable, 1);

        when(productRepository.findByActiveTrueAndCategoryId(eq(100L), any(Pageable.class))).thenReturn(page);

        ResponseEntity<?> response = apiProductController.getProducts(0, 10, 100L, null, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        List<?> content = (List<?>) body.get("content");
        assertThat(content).hasSize(1);
    }

    @Test // Test lấy chi tiết sản phẩm với ID hợp lệ -> trả về thông tin sản phẩm
    void testGetProductDetail_WithValidId_ShouldReturnProduct() {
        Product product = buildProduct(10L, "Detail", 5, true);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productRepository.findRelatedProductsByCategoryTree(anyLong(), eq(10L), any(Pageable.class)))
                .thenReturn(Page.empty());

        ResponseEntity<?> response = apiProductController.getProductById(10L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("id")).isEqualTo(10L);
        assertThat(body.get("name")).isEqualTo("Detail");
    }

    @Test // Test lấy chi tiết sản phẩm với ID không tồn tại -> phải trả về 404
    void testGetProductDetail_WithInvalidId_ShouldReturnNotFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = apiProductController.getProductById(999L);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test // Test sản phẩm với stockQuantity = 0 -> trong summary phải thể hiện số lượng tồn = 0 (out of stock)
    void testProduct_WithZeroStock_ShouldBeMarkedOutOfStock() {
        Pageable pageable = PageRequest.of(0, 10);
        List<Product> products = Collections.singletonList(buildProduct(20L, "OutOfStock", 0, true));
        Page<Product> page = new PageImpl<>(products, pageable, 1);

        when(productRepository.findByActiveTrue(pageable)).thenReturn(page);

        ResponseEntity<?> response = apiProductController.getProducts(0, 10, null, null, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        List<Map<?, ?>> content = (List<Map<?, ?>>) body.get("content");
        Map<?, ?> productSummary = content.get(0);
        assertThat(productSummary.get("stockQuantity")).isEqualTo(0);
    }

    // Hàm tiện ích dựng Product giả lập
    private Product buildProduct(Long id, String name, int stock, boolean active) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(BigDecimal.valueOf(100000));
        p.setSalePrice(BigDecimal.valueOf(90000));
        p.setStockQuantity(stock);
        p.setActive(active);
        p.setFeatured(false);
        p.setPrescriptionRequired(false);
        p.setProductCode("P" + id);

        Category c = new Category();
        c.setId(1L);
        c.setName("Category 1");
        p.setCategory(c);

        return p;
    }
}
