package com.example.vintage.controller.api;

import com.example.vintage.entity.Category;
import com.example.vintage.entity.Product;
import com.example.vintage.repository.CategoryRepository;
import com.example.vintage.repository.ProductRepository;
import com.example.vintage.service.InventoryService;
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
    private final InventoryService inventoryService;

    public ApiProductController(ProductRepository productRepository,
                                CategoryRepository categoryRepository,
                                InventoryService inventoryService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryService = inventoryService;
    }

    /**
     * GET /api/products?page=0&size=12&mainCategoryId=1&subCategoryId=5
     */
    @GetMapping("/products")
    public ResponseEntity<?> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long mainCategoryId,
            @RequestParam(required = false) Long subCategoryId) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Product> products;
            
            // Priority: subCategoryId > mainCategoryId > categoryId > all
            if (subCategoryId != null) {
                products = productRepository.findByActiveTrueAndSubCategoryId(subCategoryId, pageable);
            } else if (mainCategoryId != null) {
                products = productRepository.findByActiveTrueAndMainCategoryId(mainCategoryId, pageable);
            } else if (categoryId != null) {
                products = productRepository.findByActiveTrueAndCategoryId(categoryId, pageable);
            } else {
                products = productRepository.findByActiveTrue(pageable);
            }
            return ResponseEntity.ok(buildPageResponse(products));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
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
                    if (product.getSubCategory() != null) {
                        related = productRepository
                                .findByActiveTrueAndSubCategoryId(product.getSubCategory().getId(), PageRequest.of(0, 8))
                                .getContent().stream().filter(p -> !p.getId().equals(id)).limit(4).toList();
                    } else if (product.getMainCategory() != null) {
                        related = productRepository
                                .findByActiveTrueAndMainCategoryId(product.getMainCategory().getId(), PageRequest.of(0, 8))
                                .getContent().stream().filter(p -> !p.getId().equals(id)).limit(4).toList();
                    } else if (product.getCategory() != null) {
                        Long categoryTreeId = product.getCategory().getParent() != null
                                ? product.getCategory().getParent().getId()
                                : product.getCategory().getId();
                        related = productRepository.findRelatedProductsByCategoryTree(categoryTreeId, id, PageRequest.of(0, 4)).getContent();
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
        List<Category> mainCategories = categoryRepository.findByActiveTrueAndParentIsNullOrderByDisplayOrderAscNameAsc();
        return ResponseEntity.ok(mainCategories.stream().map(c -> toCategorySummary(c, true)).toList());
    }

    /**
     * GET /api/categories/{id}
     */
    @GetMapping("/categories/{id}")
    public ResponseEntity<?> getCategoryById(@PathVariable Long id) {
        return categoryRepository.findById(id)
                .map(c -> ResponseEntity.ok((Object) toCategorySummary(c, true)))
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
            throw new RuntimeException(e.getMessage());
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
        map.put("stockQuantity", inventoryService.getAvailableQuantity(p));
        map.put("imageUrl", p.getImageUrl());
        map.put("featured", p.isFeatured());
        map.put("prescriptionRequired", p.isPrescriptionRequired());

        Category mainCategory = p.getMainCategory();
        Category subCategory = p.getSubCategory();
        Category legacyCategory = p.getCategory();

        if (mainCategory == null && subCategory == null && legacyCategory != null) {
            mainCategory = legacyCategory.getParent() != null ? legacyCategory.getParent() : legacyCategory;
            subCategory = legacyCategory.getParent() != null ? legacyCategory : null;
        }

        Category displayCategory = subCategory != null ? subCategory : mainCategory;
        map.put("categoryId", displayCategory != null ? displayCategory.getId() : null);
        map.put("categoryName", displayCategory != null ? displayCategory.getName() : null);
        map.put("mainCategoryId", mainCategory != null ? mainCategory.getId() : null);
        map.put("mainCategoryName", mainCategory != null ? mainCategory.getName() : null);
        map.put("subCategoryId", subCategory != null ? subCategory.getId() : null);
        map.put("subCategoryName", subCategory != null ? subCategory.getName() : null);
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

    private Map<String, Object> toCategorySummary(Category c, boolean includeChildren) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", c.getId());
        map.put("name", c.getName());
        map.put("description", c.getDescription() != null ? c.getDescription() : "");
        map.put("imageUrl", c.getImageUrl() != null ? c.getImageUrl() : "");
        map.put("displayOrder", c.getDisplayOrder() != null ? c.getDisplayOrder() : 0);
        map.put("parentId", c.getParent() != null ? c.getParent().getId() : null);
        map.put("isMainCategory", c.getParent() == null);

        if (includeChildren) {
            List<Category> children = categoryRepository.findByActiveTrueAndParentIdOrderByDisplayOrderAscNameAsc(c.getId());
            map.put("subCategories", children.stream().map(child -> toCategorySummary(child, false)).toList());
        }
        return map;
    }
}

