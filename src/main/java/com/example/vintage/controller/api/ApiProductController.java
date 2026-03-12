package com.example.vintage.controller.api;

import com.example.vintage.entity.Category;
import com.example.vintage.entity.Product;
import com.example.vintage.repository.CategoryRepository;
import com.example.vintage.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api", "/api/v1"})
public class ApiProductController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ApiProductController(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * GET /api/products?page=0&size=12&categoryId=1
     */
    @GetMapping("/products")
    public ResponseEntity<?> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) Long categoryId) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Product> products;
            if (categoryId != null) {
                products = productRepository.findByActiveTrueAndCategoryId(categoryId, pageable);
            } else {
                products = productRepository.findByActiveTrue(pageable);
            }
            return ResponseEntity.ok(buildPageResponse(products));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/products/featured
     */
    @GetMapping("/products/featured")
    public ResponseEntity<?> getFeaturedProducts() {
        List<Product> products = productRepository.findByActiveTrueAndFeaturedTrue();
        return ResponseEntity.ok(products.stream().map(this::toProductSummary).toList());
    }

    /**
     * GET /api/products/{id}
     */
    @GetMapping("/products/{id}")
    public ResponseEntity<?> getProductById(@PathVariable Long id) {
        return productRepository.findById(id)
                .filter(Product::isActive)
                .map(product -> {
                    List<Product> related = List.of();
                    if (product.getCategory() != null) {
                        Page<Product> relatedPage = productRepository.findByActiveTrueAndCategoryIdAndIdNot(
                                product.getCategory().getId(), id, PageRequest.of(0, 4));
                        related = relatedPage.getContent();
                    }
                    Map<String, Object> response = new java.util.HashMap<>(toProductDetail(product));
                    response.put("relatedProducts", related.stream().map(this::toProductSummary).toList());
                    return ResponseEntity.ok((Object) response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/categories
     */
    @GetMapping("/categories")
    public ResponseEntity<?> getCategories() {
        List<Category> categories = categoryRepository.findByActiveTrueOrderByName();
        return ResponseEntity.ok(categories.stream().map(this::toCategorySummary).toList());
    }

    /**
     * GET /api/categories/{id}
     */
    @GetMapping("/categories/{id}")
    public ResponseEntity<?> getCategoryById(@PathVariable Long id) {
        return categoryRepository.findById(id)
                .map(c -> ResponseEntity.ok((Object) toCategorySummary(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/search?keyword=...&page=0&size=12
     */
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Product> products = productRepository.searchProducts(keyword, pageable);
            Map<String, Object> result = new java.util.HashMap<>(buildPageResponse(products));
            result.put("keyword", keyword);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ===== Helper methods =====

    private Map<String, Object> buildPageResponse(Page<Product> page) {
        return Map.of(
                "content", page.getContent().stream().map(this::toProductSummary).toList(),
                "totalElements", page.getTotalElements(),
                "totalPages", page.getTotalPages(),
                "currentPage", page.getNumber(),
                "pageSize", page.getSize(),
                "hasNext", page.hasNext(),
                "hasPrevious", page.hasPrevious()
        );
    }

    private Map<String, Object> toProductSummary(Product p) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", p.getId());
        map.put("name", p.getName());
        map.put("productCode", p.getProductCode());
        map.put("price", p.getPrice());
        map.put("salePrice", p.getSalePrice());
        map.put("stockQuantity", p.getStockQuantity());
        map.put("imageUrl", p.getImageUrl() != null ? "/uploads/" + p.getImageUrl() : null);
        map.put("featured", p.isFeatured());
        map.put("prescriptionRequired", p.isPrescriptionRequired());
        if (p.getCategory() != null) {
            map.put("categoryId", p.getCategory().getId());
            map.put("categoryName", p.getCategory().getName());
        }
        return map;
    }

    private Map<String, Object> toProductDetail(Product p) {
        Map<String, Object> map = new java.util.HashMap<>(toProductSummary(p));
        map.put("description", p.getDescription());
        map.put("ingredients", p.getIngredients());
        map.put("usage", p.getUsage());
        map.put("contraindications", p.getContraindications());
        map.put("manufacturer", p.getManufacturer());
        map.put("country", p.getCountry());
        map.put("dosageForm", p.getDosageForm());
        map.put("packaging", p.getPackaging());
        map.put("manufacturingDate", p.getManufacturingDate());
        map.put("expiryDate", p.getExpiryDate());
        map.put("createdAt", p.getCreatedAt());
        return map;
    }

    private Map<String, Object> toCategorySummary(Category c) {
        return Map.of(
                "id", c.getId(),
                "name", c.getName(),
                "description", c.getDescription() != null ? c.getDescription() : "",
                "imageUrl", c.getImageUrl() != null ? c.getImageUrl() : "",
                "displayOrder", c.getDisplayOrder() != null ? c.getDisplayOrder() : 0
        );
    }
}

