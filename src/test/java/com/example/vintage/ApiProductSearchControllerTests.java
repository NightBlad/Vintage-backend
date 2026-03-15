package com.example.vintage;

import com.example.vintage.controller.api.ApiProductController;
import com.example.vintage.entity.Product;
import com.example.vintage.repository.CategoryRepository;
import com.example.vintage.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ApiProductSearchControllerTests {

    private ProductRepository productRepository;
    private CategoryRepository categoryRepository;
    private ApiProductController apiProductController;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        apiProductController = new ApiProductController(productRepository, categoryRepository);
    }

    @Test // Test search sản phẩm với keyword có kết quả -> trả về danh sách sản phẩm phù hợp
    void testSearchProduct_WithKeyword_ShouldReturnResults() {
        String keyword = "aspirin";
        Pageable pageable = PageRequest.of(0, 10);

        Product p1 = new Product();
        p1.setId(100L);
        p1.setName("Aspirin 500mg");
        Product p2 = new Product();
        p2.setId(101L);
        p2.setName("Aspirin 100mg");

        List<Product> products = List.of(p1, p2);
        Page<Product> page = new PageImpl<>(products, pageable, products.size());

        when(productRepository.searchProducts(eq(keyword), any(Pageable.class))).thenReturn(page);

        ResponseEntity<?> response = apiProductController.search(keyword, 0, 10);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        var body = (java.util.Map<?, ?>) response.getBody();
        var content = (java.util.List<?>) body.get("content");
        assertThat(content).hasSize(2);
        assertThat(body.get("keyword")).isEqualTo(keyword);
    }

    @Test // Test search sản phẩm với keyword không khớp -> trả về danh sách rỗng
    void testSearchProduct_WithNoMatch_ShouldReturnEmpty() {
        String keyword = "no-such-product";
        Pageable pageable = PageRequest.of(0, 10);

        Page<Product> emptyPage = Page.empty(pageable);
        when(productRepository.searchProducts(eq(keyword), any(Pageable.class))).thenReturn(emptyPage);

        ResponseEntity<?> response = apiProductController.search(keyword, 0, 10);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        var body = (java.util.Map<?, ?>) response.getBody();
        var content = (java.util.List<?>) body.get("content");
        assertThat(content).isEmpty();
        assertThat(body.get("keyword")).isEqualTo(keyword);
    }

    @Test // Test search với keyword chứa ký tự đặc biệt -> controller không được crash, vẫn trả response 200
    void testSearchProduct_WithSpecialCharacters_ShouldNotCrash() {
        String keyword = "aspirin+500%$#";
        Pageable pageable = PageRequest.of(0, 10);

        when(productRepository.searchProducts(eq(keyword), any(Pageable.class)))
                .thenReturn(Page.empty(pageable));

        ResponseEntity<?> response = apiProductController.search(keyword, 0, 10);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        var body = (java.util.Map<?, ?>) response.getBody();
        assertThat(body.get("keyword")).isEqualTo(keyword);
    }
}

