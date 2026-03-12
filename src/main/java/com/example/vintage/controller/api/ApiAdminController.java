package com.example.vintage.controller.api;

import com.example.vintage.entity.*;
import com.example.vintage.repository.*;
import com.example.vintage.service.FileUploadService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/admin", "/api/v1/admin"})
public class ApiAdminController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final FileUploadService fileUploadService;

    public ApiAdminController(ProductRepository productRepository,
                               CategoryRepository categoryRepository,
                               OrderRepository orderRepository,
                               UserRepository userRepository,
                               RoleRepository roleRepository,
                               FileUploadService fileUploadService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.fileUploadService = fileUploadService;
    }

    // ===== DASHBOARD =====

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard() {
        return ResponseEntity.ok(Map.of(
                "totalProducts", productRepository.count(),
                "totalCategories", categoryRepository.count(),
                "totalUsers", userRepository.count(),
                "totalOrders", orderRepository.count(),
                "lowStockProducts", productRepository.findByStockQuantityLessThan(10).stream()
                        .map(p -> Map.of("id", p.getId(), "name", p.getName(), "stockQuantity", p.getStockQuantity()))
                        .collect(Collectors.toList())
        ));
    }

    // ===== PRODUCT MANAGEMENT =====

    @GetMapping("/products")
    public ResponseEntity<?> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = productRepository.findAll(pageable);
        return ResponseEntity.ok(Map.of(
                "content", products.getContent().stream().map(p -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", p.getId());
                    m.put("name", p.getName());
                    m.put("productCode", p.getProductCode());
                    m.put("price", p.getPrice());
                    m.put("salePrice", p.getSalePrice());
                    m.put("stockQuantity", p.getStockQuantity());
                    m.put("active", p.isActive());
                    m.put("featured", p.isFeatured());
                    m.put("imageUrl", p.getImageUrl() != null ? "/uploads/" + p.getImageUrl() : null);
                    if (p.getCategory() != null) {
                        m.put("categoryId", p.getCategory().getId());
                        m.put("categoryName", p.getCategory().getName());
                    }
                    return m;
                }).collect(Collectors.toList()),
                "totalElements", products.getTotalElements(),
                "totalPages", products.getTotalPages(),
                "currentPage", products.getNumber()
        ));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<?> getProduct(@PathVariable Long id) {
        return productRepository.findById(id)
                .map(p -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", p.getId());
                    m.put("name", p.getName());
                    m.put("productCode", p.getProductCode());
                    m.put("description", p.getDescription());
                    m.put("ingredients", p.getIngredients());
                    m.put("usage", p.getUsage());
                    m.put("contraindications", p.getContraindications());
                    m.put("price", p.getPrice());
                    m.put("salePrice", p.getSalePrice());
                    m.put("stockQuantity", p.getStockQuantity());
                    m.put("manufacturer", p.getManufacturer());
                    m.put("country", p.getCountry());
                    m.put("dosageForm", p.getDosageForm());
                    m.put("packaging", p.getPackaging());
                    m.put("manufacturingDate", p.getManufacturingDate());
                    m.put("expiryDate", p.getExpiryDate());
                    m.put("imageUrl", p.getImageUrl() != null ? "/uploads/" + p.getImageUrl() : null);
                    m.put("prescriptionRequired", p.isPrescriptionRequired());
                    m.put("active", p.isActive());
                    m.put("featured", p.isFeatured());
                    if (p.getCategory() != null) {
                        m.put("categoryId", p.getCategory().getId());
                        m.put("categoryName", p.getCategory().getName());
                    }
                    return ResponseEntity.ok((Object) m);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/products")
    public ResponseEntity<?> createProduct(
            @RequestParam String name,
            @RequestParam String productCode,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String ingredients,
            @RequestParam(required = false) String usage,
            @RequestParam(required = false) String contraindications,
            @RequestParam BigDecimal price,
            @RequestParam(required = false) BigDecimal salePrice,
            @RequestParam Integer stockQuantity,
            @RequestParam(required = false) String manufacturer,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String dosageForm,
            @RequestParam(required = false) String packaging,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "false") boolean prescriptionRequired,
            @RequestParam(defaultValue = "true") boolean active,
            @RequestParam(defaultValue = "false") boolean featured,
            @RequestParam(required = false) MultipartFile imageFile) {

        if (productRepository.existsByProductCode(productCode)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mã sản phẩm đã tồn tại"));
        }

        Product product = new Product();
        product.setName(name);
        product.setProductCode(productCode);
        product.setDescription(description);
        product.setIngredients(ingredients);
        product.setUsage(usage);
        product.setContraindications(contraindications);
        product.setPrice(price);
        product.setSalePrice(salePrice);
        product.setStockQuantity(stockQuantity);
        product.setManufacturer(manufacturer);
        product.setCountry(country);
        product.setDosageForm(dosageForm);
        product.setPackaging(packaging);
        product.setPrescriptionRequired(prescriptionRequired);
        product.setActive(active);
        product.setFeatured(featured);

        if (categoryId != null) {
            categoryRepository.findById(categoryId).ifPresent(product::setCategory);
        }

        if (imageFile != null && !imageFile.isEmpty()) {
            if (!fileUploadService.isValidImageFile(imageFile)) {
                return ResponseEntity.badRequest().body(Map.of("error", "File ảnh không hợp lệ (JPG, PNG, GIF, WEBP)"));
            }
            try {
                String imagePath = fileUploadService.saveFile(imageFile);
                product.setImageUrl(imagePath);
            } catch (IOException e) {
                return ResponseEntity.status(500).body(Map.of("error", "Lỗi upload ảnh: " + e.getMessage()));
            }
        }

        productRepository.save(product);
        return ResponseEntity.ok(Map.of("message", "Thêm sản phẩm thành công", "id", product.getId()));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<?> updateProduct(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String ingredients,
            @RequestParam(required = false) String usage,
            @RequestParam(required = false) String contraindications,
            @RequestParam BigDecimal price,
            @RequestParam(required = false) BigDecimal salePrice,
            @RequestParam Integer stockQuantity,
            @RequestParam(required = false) String manufacturer,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String dosageForm,
            @RequestParam(required = false) String packaging,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "false") boolean prescriptionRequired,
            @RequestParam(defaultValue = "true") boolean active,
            @RequestParam(defaultValue = "false") boolean featured,
            @RequestParam(required = false) MultipartFile imageFile) {

        Product product = productRepository.findById(id).orElse(null);
        if (product == null) return ResponseEntity.notFound().build();

        product.setName(name);
        product.setDescription(description);
        product.setIngredients(ingredients);
        product.setUsage(usage);
        product.setContraindications(contraindications);
        product.setPrice(price);
        product.setSalePrice(salePrice);
        product.setStockQuantity(stockQuantity);
        product.setManufacturer(manufacturer);
        product.setCountry(country);
        product.setDosageForm(dosageForm);
        product.setPackaging(packaging);
        product.setPrescriptionRequired(prescriptionRequired);
        product.setActive(active);
        product.setFeatured(featured);

        if (categoryId != null) {
            categoryRepository.findById(categoryId).ifPresent(product::setCategory);
        } else {
            product.setCategory(null);
        }

        if (imageFile != null && !imageFile.isEmpty()) {
            if (!fileUploadService.isValidImageFile(imageFile)) {
                return ResponseEntity.badRequest().body(Map.of("error", "File ảnh không hợp lệ"));
            }
            try {
                String oldImage = product.getImageUrl();
                String imagePath = fileUploadService.saveFile(imageFile);
                product.setImageUrl(imagePath);
                if (oldImage != null && !oldImage.isEmpty()) {
                    fileUploadService.deleteFile(oldImage);
                }
            } catch (IOException e) {
                return ResponseEntity.status(500).body(Map.of("error", "Lỗi upload ảnh: " + e.getMessage()));
            }
        }

        productRepository.save(product);
        return ResponseEntity.ok(Map.of("message", "Cập nhật sản phẩm thành công"));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) return ResponseEntity.notFound().build();
        product.setActive(false);
        productRepository.save(product);
        return ResponseEntity.ok(Map.of("message", "Xóa sản phẩm thành công"));
    }

    // ===== CATEGORY MANAGEMENT =====

    @GetMapping("/categories")
    public ResponseEntity<?> listCategories() {
        return ResponseEntity.ok(categoryRepository.findAll().stream().map(c -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("description", c.getDescription());
            m.put("active", c.isActive());
            m.put("displayOrder", c.getDisplayOrder());
            m.put("productCount", c.getProducts() != null ? c.getProducts().size() : 0);
            return m;
        }).collect(Collectors.toList()));
    }

    @PostMapping("/categories")
    public ResponseEntity<?> createCategory(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tên danh mục không được để trống"));
        }
        if (categoryRepository.existsByName(name)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tên danh mục đã tồn tại"));
        }
        Category category = new Category();
        category.setName(name.trim());
        category.setDescription((String) body.getOrDefault("description", ""));
        category.setActive(true);
        categoryRepository.save(category);
        return ResponseEntity.ok(Map.of("message", "Thêm danh mục thành công", "id", category.getId()));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<?> updateCategory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Category category = categoryRepository.findById(id).orElse(null);
        if (category == null) return ResponseEntity.notFound().build();
        String name = (String) body.get("name");
        if (name != null) category.setName(name.trim());
        if (body.containsKey("description")) category.setDescription((String) body.get("description"));
        if (body.containsKey("active")) category.setActive((Boolean) body.get("active"));
        categoryRepository.save(category);
        return ResponseEntity.ok(Map.of("message", "Cập nhật danh mục thành công"));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        Category category = categoryRepository.findById(id).orElse(null);
        if (category == null) return ResponseEntity.notFound().build();
        category.setActive(false);
        categoryRepository.save(category);
        return ResponseEntity.ok(Map.of("message", "Xóa danh mục thành công"));
    }

    // ===== ORDER MANAGEMENT =====

    @GetMapping("/orders")
    public ResponseEntity<?> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders;
        if (status != null && !status.isBlank()) {
            OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
            orders = orderRepository.findByStatusOrderByOrderDateDesc(orderStatus, pageable);
        } else {
            orders = orderRepository.findAllOrderByOrderDateDesc(pageable);
        }
        return ResponseEntity.ok(Map.of(
                "content", orders.getContent().stream().map(o -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", o.getId());
                    m.put("orderNumber", o.getOrderNumber());
                    m.put("status", o.getStatus());
                    m.put("totalAmount", o.getTotalAmount());
                    m.put("customerName", o.getCustomerName());
                    m.put("customerPhone", o.getCustomerPhone());
                    m.put("paymentMethod", o.getPaymentMethod());
                    m.put("paymentStatus", o.getPaymentStatus());
                    m.put("orderDate", o.getOrderDate());
                    if (o.getUser() != null) {
                        m.put("userId", o.getUser().getId());
                        m.put("username", o.getUser().getUsername());
                    }
                    return m;
                }).collect(Collectors.toList()),
                "totalElements", orders.getTotalElements(),
                "totalPages", orders.getTotalPages(),
                "currentPage", orders.getNumber()
        ));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getOrder(@PathVariable Long id) {
        return orderRepository.findByIdWithItems(id)
                .map(o -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", o.getId());
                    m.put("orderNumber", o.getOrderNumber());
                    m.put("status", o.getStatus());
                    m.put("totalAmount", o.getTotalAmount());
                    m.put("shippingFee", o.getShippingFee());
                    m.put("customerName", o.getCustomerName());
                    m.put("customerPhone", o.getCustomerPhone());
                    m.put("customerEmail", o.getCustomerEmail());
                    m.put("shippingAddress", o.getShippingAddress());
                    m.put("notes", o.getNotes());
                    m.put("paymentMethod", o.getPaymentMethod());
                    m.put("paymentStatus", o.getPaymentStatus());
                    m.put("orderDate", o.getOrderDate());
                    m.put("updatedAt", o.getUpdatedAt());
                    if (o.getUser() != null) {
                        m.put("userId", o.getUser().getId());
                        m.put("username", o.getUser().getUsername());
                    }
                    m.put("items", o.getOrderItems().stream().map(item -> {
                        Map<String, Object> i = new java.util.HashMap<>();
                        i.put("id", item.getId());
                        i.put("quantity", item.getQuantity());
                        i.put("price", item.getPrice());
                        i.put("totalPrice", item.getTotalPrice());
                        if (item.getProduct() != null) {
                            i.put("productId", item.getProduct().getId());
                            i.put("productName", item.getProduct().getName());
                        }
                        return i;
                    }).collect(Collectors.toList()));
                    return ResponseEntity.ok((Object) m);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/orders/{id}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) return ResponseEntity.notFound().build();
        try {
            OrderStatus status = OrderStatus.valueOf(body.get("status").toUpperCase());
            order.setStatus(status);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            return ResponseEntity.ok(Map.of("message", "Cập nhật trạng thái đơn hàng thành công"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Trạng thái không hợp lệ"));
        }
    }

    // ===== USER MANAGEMENT =====

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<User> users = userRepository.findAll(pageable);
        return ResponseEntity.ok(Map.of(
                "content", users.getContent().stream().map(u -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", u.getId());
                    m.put("username", u.getUsername());
                    m.put("email", u.getEmail());
                    m.put("fullName", u.getFullName());
                    m.put("phone", u.getPhone());
                    m.put("enabled", u.isEnabled());
                    m.put("roles", u.getRoles().stream().map(r -> r.getName().name()).collect(Collectors.toList()));
                    m.put("createdAt", u.getCreatedAt());
                    return m;
                }).collect(Collectors.toList()),
                "totalElements", users.getTotalElements(),
                "totalPages", users.getTotalPages(),
                "currentPage", users.getNumber()
        ));
    }

    @PutMapping("/users/{id}/toggle-status")
    public ResponseEntity<?> toggleUserStatus(@PathVariable Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
        return ResponseEntity.ok(Map.of(
                "message", user.isEnabled() ? "Đã kích hoạt tài khoản" : "Đã vô hiệu hóa tài khoản",
                "enabled", user.isEnabled()
        ));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> "ROLE_ADMIN".equals(role.getName().name()));
        if (isAdmin) {
            return ResponseEntity.badRequest().body(Map.of("error", "Không thể xóa tài khoản Admin"));
        }
        user.setEnabled(false);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Xóa người dùng thành công"));
    }
}

