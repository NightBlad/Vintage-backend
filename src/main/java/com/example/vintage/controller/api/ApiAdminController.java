package com.example.vintage.controller.api;

import com.example.vintage.dto.ProductDTO;
import com.example.vintage.entity.*;
import com.example.vintage.repository.*;
import com.example.vintage.service.FileUploadService;
import com.example.vintage.service.InventoryService;
import com.example.vintage.service.OrderService;
import com.example.vintage.service.ProductService;
import com.example.vintage.entity.RoleName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/admin", "/api/v1/admin"})
public class ApiAdminController {

    private static final int DASHBOARD_LOW_STOCK_THRESHOLD = 10;
    private static final int DASHBOARD_RECENT_ORDER_LIMIT = 10;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final FileUploadService fileUploadService;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final OrderService orderService;

    public ApiAdminController(ProductRepository productRepository,
                               CategoryRepository categoryRepository,
                               OrderRepository orderRepository,
                               UserRepository userRepository,
                               RoleRepository roleRepository,
                               FileUploadService fileUploadService,
                               ProductService productService,
                               InventoryService inventoryService,
                               OrderService orderService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.fileUploadService = fileUploadService;
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.orderService = orderService;
    }

    // ===== DASHBOARD =====

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard() {
        List<Product> lowStockProducts = productRepository.findByStockQuantityLessThan(DASHBOARD_LOW_STOCK_THRESHOLD);
        List<Order> recentOrders = orderRepository.findAllOrderByOrderDateDesc(PageRequest.of(0, DASHBOARD_RECENT_ORDER_LIMIT)).getContent();

        List<Map<String, Object>> recentOrderPayload = recentOrders.stream()
                .map(this::toDashboardOrder)
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalProducts", productRepository.count());
        response.put("totalCategories", categoryRepository.count());
        response.put("totalUsers", userRepository.count());
        response.put("totalOrders", orderRepository.count());
        response.put("lowStockCount", lowStockProducts.size());
        response.put("lowStockProducts", lowStockProducts.stream().map(this::toDashboardProduct).collect(Collectors.toList()));
        response.put("inventoryValue", calculateInventoryValue());
        response.put("recentOrders", recentOrderPayload);
        response.put("salesSummary", buildSalesSummary(recentOrders));
        response.put("timeSeries", buildTimeSeriesCharts());
        response.put("revenueSummary", buildRevenueSummary());

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> buildSalesSummary(List<Order> recentOrders) {
        BigDecimal deliveredRevenue = recentOrders.stream()
                .filter(order -> order.getStatus() == OrderStatus.DELIVERED)
                .map(order -> order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long deliveredCount = recentOrders.stream().filter(order -> order.getStatus() == OrderStatus.DELIVERED).count();
        long cancelledCount = recentOrders.stream().filter(order -> order.getStatus() == OrderStatus.CANCELLED).count();

        BigDecimal recentAov = deliveredCount > 0
                ? deliveredRevenue.divide(BigDecimal.valueOf(deliveredCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        double cancellationRate = recentOrders.isEmpty()
                ? 0.0d
                : BigDecimal.valueOf(cancelledCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(recentOrders.size()), 2, RoundingMode.HALF_UP)
                .doubleValue();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("recentOrderCount", recentOrders.size());
        summary.put("recentRevenue", deliveredRevenue);
        summary.put("recentAov", recentAov);
        summary.put("recentCancellationRate", cancellationRate);
        summary.put("topSellingProducts", buildTopSellingProducts(recentOrders));
        summary.put("statusStats", buildStatusStats(recentOrders));
        summary.put("insights", buildDashboardInsights(recentOrders, cancellationRate));
        return summary;
    }

    private List<Map<String, Object>> buildTopSellingProducts(List<Order> recentOrders) {
        Map<Long, Map<String, Object>> aggregates = new LinkedHashMap<>();

        for (Order order : recentOrders) {
            if (order.getOrderItems() == null) {
                continue;
            }
            for (OrderItem item : order.getOrderItems()) {
                Product product = item.getProduct();
                Long productId = product != null ? product.getId() : null;
                String productName = product != null ? product.getName() : "Unknown Product";
                Long key = productId != null ? productId : -1L;

                Map<String, Object> aggregate = aggregates.computeIfAbsent(key, ignored -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("productId", productId);
                    row.put("productName", productName);
                    row.put("quantity", 0);
                    row.put("revenue", BigDecimal.ZERO);
                    return row;
                });

                int quantity = item.getQuantity() != null ? item.getQuantity() : 0;
                BigDecimal lineRevenue = item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO;
                aggregate.put("quantity", (Integer) aggregate.get("quantity") + quantity);
                aggregate.put("revenue", ((BigDecimal) aggregate.get("revenue")).add(lineRevenue));
            }
        }

        return aggregates.values().stream()
                .sorted((a, b) -> Integer.compare((Integer) b.get("quantity"), (Integer) a.get("quantity")))
                .limit(5)
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildStatusStats(List<Order> recentOrders) {
        Map<String, Long> counts = recentOrders.stream()
                .collect(Collectors.groupingBy(order -> normalizeDashboardStatus(order.getStatus()), LinkedHashMap::new, Collectors.counting()));

        int total = recentOrders.size();
        return counts.entrySet().stream().map(entry -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", entry.getKey());
            row.put("count", entry.getValue());
            double share = total == 0
                    ? 0.0d
                    : BigDecimal.valueOf(entry.getValue())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                    .doubleValue();
            row.put("share", share);
            return row;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildDashboardInsights(List<Order> recentOrders, double cancellationRate) {
        List<Map<String, Object>> insights = new ArrayList<>();

        if (recentOrders.isEmpty()) {
            insights.add(Map.of(
                    "tone", "info",
                    "message", "Chua co don hang gan day de phan tich."
            ));
            return insights;
        }

        if (cancellationRate >= 20.0d) {
            insights.add(Map.of(
                    "tone", "warning",
                    "message", "Ti le huy don gan day cao, hay ra soat quy trinh xac nhan don.",
                    "link", "/admin/orders?status=CANCELLED",
                    "linkLabel", "Xem don bi huy"
            ));
        } else {
            insights.add(Map.of(
                    "tone", "success",
                    "message", "Tỉ lệ hủy đơn đang ở mức ổn định trong nhóm đơn gần đây."
            ));
        }

        long deliveredCount = recentOrders.stream().filter(order -> order.getStatus() == OrderStatus.DELIVERED).count();
        if (deliveredCount == 0) {
            insights.add(Map.of(
                    "tone", "info",
                    "message", "Chua co don DELIVERED trong tap don gan day."
            ));
        }

        return insights;
    }

    private Map<String, Object> buildRevenueSummary() {
        LocalDate today = LocalDate.now();
        LocalDateTime startToday = today.atStartOfDay();
        LocalDateTime startWeek = today.with(java.time.DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime startMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        BigDecimal revenueToday = sumRevenue(startToday, end);
        BigDecimal revenueWeek = sumRevenue(startWeek, end);
        BigDecimal revenueMonth = sumRevenue(startMonth, end);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("today", revenueToday);
        summary.put("thisWeek", revenueWeek);
        summary.put("thisMonth", revenueMonth);
        return summary;
    }

    private String normalizeDashboardStatus(OrderStatus status) {
        if (status == null) {
            return "PENDING";
        }
        if (status == OrderStatus.SHIPPED) {
            return "SHIPPING";
        }
        return status.name();
    }

    private Map<String, Object> buildTimeSeriesCharts() {
        LocalDate today = LocalDate.now();

        List<Map<String, Object>> daily = buildDailySeries(today.minusDays(6), today);
        List<Map<String, Object>> weekly = buildWeeklySeries(today.minusWeeks(7), today);
        List<Map<String, Object>> monthly = buildMonthlySeries(today.minusMonths(5), today);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("daily", daily);
        payload.put("weekly", weekly);
        payload.put("monthly", monthly);
        return payload;
    }

    private List<Map<String, Object>> buildDailySeries(LocalDate start, LocalDate end) {
        List<Order> orders = safeFindOrders(start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        Map<LocalDate, List<Order>> grouped = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getOrderDate().toLocalDate()));

        List<Map<String, Object>> rows = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            List<Order> bucket = grouped.getOrDefault(cursor, List.of());
            rows.add(timeSeriesRow(cursor.toString(), bucket));
            cursor = cursor.plusDays(1);
        }
        return rows;
    }

    private List<Map<String, Object>> buildWeeklySeries(LocalDate start, LocalDate end) {
        List<Order> orders = safeFindOrders(start.with(java.time.DayOfWeek.MONDAY).atStartOfDay(), end.plusDays(1).atStartOfDay());
        Map<String, List<Order>> grouped = orders.stream().collect(Collectors.groupingBy(o -> {
            LocalDate d = o.getOrderDate().toLocalDate();
            java.time.temporal.WeekFields wf = java.time.temporal.WeekFields.ISO;
            int week = d.get(wf.weekOfWeekBasedYear());
            int year = d.get(wf.weekBasedYear());
            return year + "-W" + String.format("%02d", week);
        }));

        List<Map<String, Object>> rows = new ArrayList<>();
        LocalDate cursor = start.with(java.time.DayOfWeek.MONDAY);
        java.time.temporal.WeekFields wf = java.time.temporal.WeekFields.ISO;
        while (!cursor.isAfter(end)) {
            int week = cursor.get(wf.weekOfWeekBasedYear());
            int year = cursor.get(wf.weekBasedYear());
            String key = year + "-W" + String.format("%02d", week);
            rows.add(timeSeriesRow(key, grouped.getOrDefault(key, List.of())));
            cursor = cursor.plusWeeks(1);
        }
        return rows;
    }

    private List<Map<String, Object>> buildMonthlySeries(LocalDate start, LocalDate end) {
        List<Order> orders = safeFindOrders(start.withDayOfMonth(1).atStartOfDay(), end.plusMonths(1).withDayOfMonth(1).atStartOfDay());
        Map<String, List<Order>> grouped = orders.stream().collect(Collectors.groupingBy(o -> {
            LocalDate d = o.getOrderDate().toLocalDate();
            return d.getYear() + "-" + String.format("%02d", d.getMonthValue());
        }));

        List<Map<String, Object>> rows = new ArrayList<>();
        LocalDate cursor = start.withDayOfMonth(1);
        while (!cursor.isAfter(end)) {
            String key = cursor.getYear() + "-" + String.format("%02d", cursor.getMonthValue());
            rows.add(timeSeriesRow(key, grouped.getOrDefault(key, List.of())));
            cursor = cursor.plusMonths(1);
        }
        return rows;
    }

    private List<Order> safeFindOrders(LocalDateTime start, LocalDateTime end) {
        List<Order> orders = orderRepository.findOrdersBetweenDates(start, end);
        return orders != null ? orders : List.of();
    }

    private BigDecimal sumRevenue(LocalDateTime start, LocalDateTime end) {
        return safeFindOrders(start, end).stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, Object> timeSeriesRow(String label, List<Order> orders) {
        BigDecimal revenue = orders.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int count = orders.size();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("orderCount", count);
        row.put("revenue", revenue);
        return row;
    }

    private BigDecimal calculateInventoryValue() {
        return productRepository.findAll().stream()
                .map(p -> {
                    BigDecimal unit = (p.getSalePrice() != null && p.getSalePrice().compareTo(BigDecimal.ZERO) > 0)
                            ? p.getSalePrice()
                            : (p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO);
                    int stock = p.getStockQuantity() != null ? p.getStockQuantity() : 0;
                    return unit.multiply(BigDecimal.valueOf(stock));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isAdminUser(User user) {
        return user.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ROLE_ADMIN);
    }

    private Map<String, Object> toDashboardProduct(Product product) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", product.getId());
        payload.put("name", product.getName());
        payload.put("productCode", product.getProductCode());
        payload.put("price", product.getPrice());
        payload.put("salePrice", product.getSalePrice());
        payload.put("stockQuantity", product.getStockQuantity());
        payload.put("imageUrl", product.getImageUrl());
        payload.put("active", product.isActive());
        return payload;
    }

    private Map<String, Object> toDashboardOrder(Order order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", order.getId());
        payload.put("orderNumber", order.getOrderNumber());
        payload.put("status", normalizeDashboardStatus(order.getStatus()));
        payload.put("paymentMethod", order.getPaymentMethod());
        payload.put("paymentStatus", order.getPaymentStatus());
        payload.put("totalAmount", order.getTotalAmount());
        payload.put("shippingFee", order.getShippingFee());
        payload.put("customerName", order.getCustomerName());
        payload.put("customerPhone", order.getCustomerPhone());
        payload.put("shippingAddress", order.getShippingAddress());
        payload.put("orderDate", order.getOrderDate());

        List<Map<String, Object>> items = order.getOrderItems() == null ? List.of() : order.getOrderItems().stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("quantity", item.getQuantity());
            row.put("price", item.getPrice());
            row.put("totalPrice", item.getTotalPrice());
            if (item.getProduct() != null) {
                row.put("productId", item.getProduct().getId());
                row.put("productName", item.getProduct().getName());
                row.put("productCode", item.getProduct().getProductCode());
                row.put("productImage", item.getProduct().getImageUrl() != null ? "/uploads/" + item.getProduct().getImageUrl() : null);
            }
            return row;
        }).collect(Collectors.toList());
        payload.put("orderItems", items);
        payload.put("itemCount", items.size());
        return payload;
    }

    // ===== PRODUCT MANAGEMENT =====

    @GetMapping("/products")
    public ResponseEntity<?> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String q) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = (q != null && !q.isBlank())
                ? productRepository.searchAllForAdmin(q.trim(), pageable)
                : productRepository.findAll(pageable);
        return ResponseEntity.ok(Map.of(
                "content", products.getContent().stream().map(p -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", p.getId());
                    m.put("name", p.getName());
                    m.put("productCode", p.getProductCode());
                    m.put("price", p.getPrice());
                    m.put("salePrice", p.getSalePrice());
                    m.put("stockQuantity", inventoryService.getAvailableQuantity(p));
                    m.put("active", p.isActive());
                    m.put("featured", p.isFeatured());
                    m.put("imageUrl", p.getImageUrl());
                    appendProductCategoryInfo(m, p);
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
                    m.put("stockQuantity", inventoryService.getAvailableQuantity(p));
                    m.put("manufacturer", p.getManufacturer());
                    m.put("country", p.getCountry());
                    m.put("dosageForm", p.getDosageForm());
                    m.put("packaging", p.getPackaging());
                    m.put("manufacturingDate", p.getManufacturingDate());
                    m.put("expiryDate", p.getExpiryDate());
                    m.put("imageUrl", p.getImageUrl());
                    m.put("additionalImages", p.getAdditionalImages() != null ? p.getAdditionalImages() : List.of());
                    m.put("prescriptionRequired", p.isPrescriptionRequired());
                    m.put("active", p.isActive());
                    m.put("featured", p.isFeatured());
                    m.put("inventoryDetails", inventoryService.getInventoryByProduct(p.getId()));
                    appendProductCategoryInfo(m, p);
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
            @RequestParam(required = false) Long mainCategoryId,
            @RequestParam(required = false) Long subCategoryId,
            @RequestParam(defaultValue = "false") boolean prescriptionRequired,
            @RequestParam(defaultValue = "true") boolean active,
            @RequestParam(defaultValue = "false") boolean featured,
            @RequestParam(required = false) MultipartFile imageFile,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) List<MultipartFile> additionalImageFiles,
            @RequestParam(required = false) List<MultipartFile> additionalImages) {

        try {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Tên sản phẩm bắt buộc");
            }
            if (productCode == null || productCode.isBlank()) {
                throw new IllegalArgumentException("Mã sản phẩm bắt buộc");
            }
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Giá phải lớn hơn 0");
            }
            if (stockQuantity == null || stockQuantity < 0) {
                throw new IllegalArgumentException("Số lượng không được âm");
            }
            if (mainCategoryId == null && categoryId == null) {
                throw new IllegalArgumentException("mainCategoryId bắt buộc");
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

            resolveAndSetCategories(product, categoryId, mainCategoryId, subCategoryId);

            MultipartFile mainImage = resolveMainImage(imageFile, image);
            if (mainImage != null && !mainImage.isEmpty()) {
                if (!fileUploadService.isValidImageFile(mainImage)) {
                    throw new IllegalArgumentException("File phải là JPG, PNG, WEBP, max 5MB");
                }
                product.setImageUrl(fileUploadService.saveFile(mainImage));
            }

            List<MultipartFile> extraFiles = resolveAdditionalFiles(additionalImageFiles, additionalImages);
            List<String> savedAdditional = new ArrayList<>();
            for (MultipartFile file : extraFiles) {
                if (file != null && !file.isEmpty()) {
                    if (!fileUploadService.isValidImageFile(file)) {
                        throw new IllegalArgumentException("File phải là JPG, PNG, WEBP, max 5MB");
                    }
                    savedAdditional.add(fileUploadService.saveFile(file));
                }
            }
            product.setAdditionalImages(savedAdditional);

            Product saved = productService.createProduct(product);
            ProductDTO dto = productService.toDTO(saved);

            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("Lỗi upload ảnh: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage() != null ? e.getMessage() : "Lỗi tạo sản phẩm");
        }
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<?> updateProduct(
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String ingredients,
            @RequestParam(required = false) String usage,
            @RequestParam(required = false) String contraindications,
            @RequestParam(required = false) BigDecimal price,
            @RequestParam(required = false) BigDecimal salePrice,
            @RequestParam(required = false) Integer stockQuantity,
            @RequestParam(required = false) String manufacturer,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String dosageForm,
            @RequestParam(required = false) String packaging,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long mainCategoryId,
            @RequestParam(required = false) Long subCategoryId,
            @RequestParam(required = false) Boolean prescriptionRequired,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) MultipartFile imageFile,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) List<MultipartFile> additionalImageFiles,
            @RequestParam(required = false) List<MultipartFile> additionalImages,
            @RequestParam(required = false) String removeAdditionalImages) {

        try {
            Product existing = productRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại"));

            if (name != null) existing.setName(name);
            if (productCode != null) existing.setProductCode(productCode);
            if (description != null) existing.setDescription(description);
            if (ingredients != null) existing.setIngredients(ingredients);
            if (usage != null) existing.setUsage(usage);
            if (contraindications != null) existing.setContraindications(contraindications);
            if (price != null) existing.setPrice(price);
            if (salePrice != null) existing.setSalePrice(salePrice);
            if (manufacturer != null) existing.setManufacturer(manufacturer);
            if (country != null) existing.setCountry(country);
            if (dosageForm != null) existing.setDosageForm(dosageForm);
            if (packaging != null) existing.setPackaging(packaging);
            if (prescriptionRequired != null) existing.setPrescriptionRequired(prescriptionRequired);
            if (active != null) existing.setActive(active);
            if (featured != null) existing.setFeatured(featured);

            MultipartFile mainImage = resolveMainImage(imageFile, image);
            if (mainImage != null && !mainImage.isEmpty()) {
                if (!fileUploadService.isValidImageFile(mainImage)) {
                    throw new IllegalArgumentException("File phải là JPG, PNG, WEBP, max 5MB");
                }
                String oldImage = existing.getImageUrl();
                existing.setImageUrl(fileUploadService.saveFile(mainImage));
                if (oldImage != null && !oldImage.isEmpty()) {
                    fileUploadService.deleteFile(oldImage);
                }
            }

            // Handle removal of additional images
            List<String> currentAdditional = existing.getAdditionalImages() != null
                    ? new ArrayList<>(existing.getAdditionalImages()) : new ArrayList<>();
            if (removeAdditionalImages != null && !removeAdditionalImages.isBlank()) {
                List<String> toRemove = Arrays.asList(removeAdditionalImages.split(","));
                for (String imgUrl : toRemove) {
                    String trimmed = imgUrl.trim();
                    if (currentAdditional.remove(trimmed)) {
                        fileUploadService.deleteFile(trimmed);
                    }
                }
            }

            // Handle new additional images
            List<MultipartFile> extraFiles = resolveAdditionalFiles(additionalImageFiles, additionalImages);
            for (MultipartFile file : extraFiles) {
                if (file != null && !file.isEmpty()) {
                    if (!fileUploadService.isValidImageFile(file)) {
                        throw new IllegalArgumentException("File phải là JPG, PNG, WEBP, max 5MB");
                    }
                    currentAdditional.add(fileUploadService.saveFile(file));
                }
            }
            existing.setAdditionalImages(currentAdditional);

            resolveAndSetCategories(existing, categoryId, mainCategoryId, subCategoryId);

            Product updated = productService.updateProduct(id, existing);

            // Adjust stock via inventory system if stockQuantity param is provided
            if (stockQuantity != null) {
                int currentInventoryStock = inventoryService.getAvailableQuantity(updated);
                int delta = stockQuantity - currentInventoryStock;
                if (delta != 0) {
                    Warehouse warehouse = inventoryService.getDefaultWarehouse();
                    if (delta > 0) {
                        inventoryService.importStock(updated, warehouse, delta, null, "Điều chỉnh từ admin");
                    } else {
                        inventoryService.exportStock(updated, warehouse, Math.abs(delta), null, "Điều chỉnh từ admin");
                    }
                }
            }

            ProductDTO dto = productService.toDTO(updated);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("Lỗi upload ảnh: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage() != null ? e.getMessage() : "Lỗi cập nhật sản phẩm");
        }
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        try {
            productService.hardDeleteProduct(id);
            return ResponseEntity.noContent().build(); // 204 No Content
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage() != null ? e.getMessage() : "Lỗi xóa sản phẩm");
        }
    }

    // ===== CATEGORY MANAGEMENT =====

    @GetMapping("/categories")
    public ResponseEntity<?> listCategories() {
        List<Category> mainCategories = categoryRepository.findAll().stream()
                .filter(c -> c.getParent() == null)
                .sorted(Comparator.comparing((Category c) -> c.getDisplayOrder() != null ? c.getDisplayOrder() : 0)
                        .thenComparing(Category::getName))
                .collect(Collectors.toList());
        return ResponseEntity.ok(mainCategories.stream().map(c -> toCategoryAdminSummary(c, true)).collect(Collectors.toList()));
    }

    @PostMapping("/categories")
    public ResponseEntity<?> createCategory(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        Long parentId = body.get("parentId") != null ? Long.valueOf(body.get("parentId").toString()) : null;
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tên danh mục không được để trống"));
        }
        if (categoryRepository.existsByName(name.trim())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tên danh mục đã tồn tại"));
        }

        Category parent = null;
        if (parentId != null) {
            parent = categoryRepository.findById(parentId).orElse(null);
            if (parent == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Danh mục chính không tồn tại"));
            }
            if (parent.getParent() != null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Chỉ hỗ trợ 2 cấp danh mục"));
            }
        }

        Category category = new Category();
        category.setName(name.trim());
        category.setDescription((String) body.getOrDefault("description", ""));
        category.setActive(true);
        category.setParent(parent);
        categoryRepository.save(category);
        return ResponseEntity.ok(Map.of("message", "Thêm danh mục thành công", "id", category.getId()));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<?> updateCategory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Category category = categoryRepository.findById(id).orElse(null);
        if (category == null) return ResponseEntity.notFound().build();

        String name = (String) body.get("name");
        if (name != null) {
            String normalizedName = name.trim();
            if (normalizedName.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Tên danh mục không được để trống"));
            }
            if (categoryRepository.existsByNameAndIdNot(normalizedName, category.getId())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Tên danh mục đã tồn tại"));
            }
            category.setName(normalizedName);
        }

        if (body.containsKey("description")) category.setDescription((String) body.get("description"));
        if (body.containsKey("active")) category.setActive((Boolean) body.get("active"));

        if (body.containsKey("parentId")) {
            Object parentRaw = body.get("parentId");
            Category parent = null;
            if (parentRaw != null) {
                Long parentId = Long.valueOf(parentRaw.toString());
                if (parentId.equals(category.getId())) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Danh mục không thể là cha của chính nó"));
                }
                parent = categoryRepository.findById(parentId).orElse(null);
                if (parent == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Danh mục chính không tồn tại"));
                }
                if (parent.getParent() != null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Chỉ hỗ trợ 2 cấp danh mục"));
                }
            }

            if (parent == null && !category.getChildren().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Không thể chuyển thành danh mục chính khi đang có danh mục phụ"));
            }
            category.setParent(parent);
        }

        categoryRepository.save(category);
        return ResponseEntity.ok(Map.of("message", "Cập nhật danh mục thành công"));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        Category category = categoryRepository.findById(id).orElse(null);
        if (category == null) return ResponseEntity.notFound().build();
        category.setActive(false);

        // Deactivate subcategories when deleting a main category.
        if (category.getParent() == null) {
            List<Category> subCategories = categoryRepository.findByParentId(category.getId());
            subCategories.forEach(sub -> sub.setActive(false));
            categoryRepository.saveAll(subCategories);
        }

        categoryRepository.save(category);
        return ResponseEntity.ok(Map.of("message", "Xóa danh mục thành công"));
    }

    private void appendProductCategoryInfo(Map<String, Object> map, Product product) {
        Category mainCategory = product.getMainCategory();
        Category subCategory = product.getSubCategory();
        Category category = product.getCategory();

        // Fallback to legacy mapping for old records
        if (mainCategory == null && subCategory == null && category != null) {
            mainCategory = category.getParent() != null ? category.getParent() : category;
            subCategory = category.getParent() != null ? category : null;
        }

        if (mainCategory == null && subCategory == null && category == null) {
            map.put("categoryId", null);
            map.put("categoryName", null);
            map.put("mainCategoryId", null);
            map.put("mainCategoryName", null);
            map.put("subCategoryId", null);
            map.put("subCategoryName", null);
            return;
        }

        Category displayCategory = subCategory != null ? subCategory : mainCategory;
        map.put("categoryId", displayCategory != null ? displayCategory.getId() : null);
        map.put("categoryName", displayCategory != null ? displayCategory.getName() : null);
        map.put("mainCategoryId", mainCategory != null ? mainCategory.getId() : null);
        map.put("mainCategoryName", mainCategory != null ? mainCategory.getName() : null);
        map.put("subCategoryId", subCategory != null ? subCategory.getId() : null);
        map.put("subCategoryName", subCategory != null ? subCategory.getName() : null);
    }

    private Map<String, Object> toCategoryAdminSummary(Category category, boolean includeChildren) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("id", category.getId());
        result.put("name", category.getName());
        result.put("description", category.getDescription());
        result.put("active", category.isActive());
        result.put("displayOrder", category.getDisplayOrder());
        result.put("productCount", category.getProducts() != null ? category.getProducts().size() : 0);
        result.put("parentId", category.getParent() != null ? category.getParent().getId() : null);
        result.put("isMainCategory", category.getParent() == null);

        if (includeChildren) {
            List<Category> subCategories = category.getChildren().stream()
                    .sorted(Comparator.comparing((Category c) -> c.getDisplayOrder() != null ? c.getDisplayOrder() : 0)
                            .thenComparing(Category::getName))
                    .collect(Collectors.toList());
            result.put("subCategories", subCategories.stream().map(sub -> toCategoryAdminSummary(sub, false)).collect(Collectors.toList()));
        }
        return result;
    }

    // ===== ORDER MANAGEMENT =====

    @GetMapping("/orders")
    public ResponseEntity<?> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders;
        if (status != null && !status.isBlank()) {
            OrderStatus orderStatus = parseOrderStatus(status);
            orders = (q != null && !q.isBlank())
                    ? orderRepository.searchByStatus(orderStatus, q.trim(), pageable)
                    : orderRepository.findByStatusOrderByOrderDateDesc(orderStatus, pageable);
        } else {
            orders = (q != null && !q.isBlank())
                    ? orderRepository.search(q.trim(), pageable)
                    : orderRepository.findAllOrderByOrderDateDesc(pageable);
        }
        return ResponseEntity.ok(Map.of(
                "content", orders.getContent().stream().map(o -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", o.getId());
                    m.put("orderNumber", o.getOrderNumber());
                    m.put("status", toFrontendStatus(o.getStatus()));
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
                .map(o -> ResponseEntity.ok((Object) toAdminOrderDetail(o)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/orders/{id}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) return ResponseEntity.notFound().build();
        try {
            String requestedStatus = body != null ? body.get("status") : null;
            if (requestedStatus == null || requestedStatus.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Thiếu trạng thái đơn hàng"));
            }
            OrderStatus status = parseOrderStatus(requestedStatus);
            orderService.updateOrderStatus(order.getId(), status);
            Order updatedOrder = orderRepository.findByIdWithItems(order.getId())
                    .orElseThrow(() -> new IllegalStateException("Không thể tải lại đơn hàng sau khi cập nhật"));
            return ResponseEntity.ok(toAdminOrderDetail(updatedOrder));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/orders/{id}/payment-status")
    public ResponseEntity<?> updatePaymentStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String statusStr = body != null ? body.get("paymentStatus") : null;
        if (statusStr == null || statusStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "paymentStatus is required"));
        }
        try {
            PaymentStatus paymentStatus = PaymentStatus.valueOf(statusStr.toUpperCase());

            Order order = orderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

            order.setPaymentStatus(paymentStatus);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);

            return ResponseEntity.ok(Map.of(
                    "message", "Cập nhật trạng thái thanh toán thành công",
                    "orderId", order.getId(),
                    "paymentStatus", order.getPaymentStatus().name()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Giá trị paymentStatus không hợp lệ"));
        }
    }

    private OrderStatus parseOrderStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ");
        }

        String normalized = rawStatus.trim().toUpperCase();
        if ("SHIPPING".equals(normalized)) {
            return OrderStatus.SHIPPED;
        }
        return OrderStatus.valueOf(normalized);
    }

    private String toFrontendStatus(OrderStatus status) {
        if (status == null) {
            return "PENDING";
        }
        return status == OrderStatus.SHIPPED ? "SHIPPING" : status.name();
    }

    private Map<String, Object> toAdminOrderDetail(Order order) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("id", order.getId());
        m.put("orderNumber", order.getOrderNumber());
        m.put("status", toFrontendStatus(order.getStatus()));
        m.put("totalAmount", order.getTotalAmount());
        m.put("shippingFee", order.getShippingFee());
        m.put("discount", order.getDiscount());
        m.put("customerName", order.getCustomerName());
        m.put("customerPhone", order.getCustomerPhone());
        m.put("customerEmail", order.getCustomerEmail());
        m.put("shippingAddress", order.getShippingAddress());
        m.put("notes", order.getNotes());
        m.put("paymentMethod", order.getPaymentMethod());
        m.put("paymentStatus", order.getPaymentStatus());
        m.put("orderDate", order.getOrderDate());
        m.put("updatedAt", order.getUpdatedAt());
        if (order.getUser() != null) {
            m.put("userId", order.getUser().getId());
            m.put("username", order.getUser().getUsername());
        }

        List<Map<String, Object>> items = order.getOrderItems().stream().map(item -> {
            Map<String, Object> i = new java.util.HashMap<>();
            i.put("id", item.getId());
            i.put("quantity", item.getQuantity());
            i.put("price", item.getPrice());
            i.put("unitPrice", item.getPrice());
            i.put("totalPrice", item.getTotalPrice());
            i.put("subtotal", item.getTotalPrice());
            if (item.getProduct() != null) {
                i.put("productId", item.getProduct().getId());
                i.put("productName", item.getProduct().getName());
                i.put("productCode", item.getProduct().getProductCode());
                i.put("productImage", item.getProduct().getImageUrl() != null ? "/uploads/" + item.getProduct().getImageUrl() : null);
            }
            return i;
        }).collect(Collectors.toList());

        m.put("orderItems", items);
        m.put("items", items);
        m.put("itemCount", items.size());
        return m;
    }

    @PutMapping("/orders/{id}/payment")
    public ResponseEntity<?> confirmOrderPayment(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        try {
            String transactionRef = (body != null) ? body.get("transactionRef") : null;
            Order order = orderService.confirmPayment(id, transactionRef);
            return ResponseEntity.ok(Map.of(
                    "message", "Xác nhận thanh toán thành công",
                    "orderId", order.getId(),
                    "paymentStatus", order.getPaymentStatus().name()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ===== USER MANAGEMENT =====

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String q) {
        Pageable pageable = PageRequest.of(page, size);
        Page<User> users = (q != null && !q.isBlank())
                ? userRepository.search(q.trim(), pageable)
                : userRepository.findAll(pageable);
        return ResponseEntity.ok(Map.of(
                "content", users.getContent().stream().map(u -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", u.getId());
                    m.put("username", u.getUsername());
                    m.put("email", u.getEmail());
                    m.put("fullName", u.getFullName());
                    m.put("phone", u.getPhone());
                    m.put("address", u.getAddress());
                    m.put("enabled", u.isEnabled());

                    // THÊM DÒNG NÀY ĐỂ GIỮ TRẠNG THÁI KHÓA KHI LOAD LẠI TRANG
                    m.put("accountLocked", u.isAccountLocked());

                    m.put("roles", u.getRoles().stream()
                            .map(r -> r.getName().name())
                            .collect(Collectors.toList()));
                    m.put("createdAt", u.getCreatedAt());
                    return m;
                }).collect(Collectors.toList()),
                "totalElements", users.getTotalElements(),
                "totalPages", users.getTotalPages(),
                "currentPage", users.getNumber()
        ));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
        response.put("fullName", user.getFullName());
        response.put("phone", user.getPhone());
        response.put("address", user.getAddress());
        response.put("enabled", user.isEnabled());
        response.put("accountLocked", user.isAccountLocked());
        response.put("roles", user.getRoles().stream().map(r -> r.getName().name()).toList());
        response.put("createdAt", user.getCreatedAt());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/users/{id}/toggle-status")
    public ResponseEntity<?> toggleUserStatus(@PathVariable Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        if (isAdminUser(user)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Không thể khóa/kích hoạt tài khoản Admin"));
        }
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

    @PostMapping("/users/{id}/toggle-lock")
    @Transactional
    public ResponseEntity<?> toggleUserLock(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        if (isAdminUser(user)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Không thể khóa tài khoản Admin"));
        }

        // Thực hiện đảo ngược trạng thái khóa
        user.setAccountLocked(!user.isAccountLocked());
        if (!user.isAccountLocked()) {
            user.setFailedAttempts(0); // Reset số lần thử nếu mở khóa
        }

        User updatedUser = userRepository.save(user);

        // Trả về Object đầy đủ để Frontend update lại mảng users mà không bị lỗi
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("id", updatedUser.getId());
        response.put("username", updatedUser.getUsername());
        response.put("email", updatedUser.getEmail());
        response.put("fullName", updatedUser.getFullName());
        response.put("accountLocked", updatedUser.isAccountLocked());
        response.put("enabled", updatedUser.isEnabled());
        response.put("roles", updatedUser.getRoles().stream()
                .map(r -> r.getName().name())
                .collect(Collectors.toList()));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/{id}/toggle-role")
    @Transactional
    public ResponseEntity<?> toggleUserRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Principal principal) { // Thêm tham số Principal để lấy user đang đăng nhập

        User userToUpdate = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        // Lấy tên đăng nhập của Admin đang thao tác
        String currentAdminUsername = principal.getName();

        // 1. NGĂN CHẶN TỰ SỬA QUYỀN CỦA CHÍNH MÌNH
        if (userToUpdate.getUsername().equals(currentAdminUsername)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bạn không thể tự thay đổi quyền hạn của chính mình để tránh mất quyền truy cập!"
            ));
        }

        String roleNameStr = body.get("role");

        try {
            RoleName targetRoleName = RoleName.valueOf(roleNameStr);
            Role role = roleRepository.findByName(targetRoleName)
                    .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy quyền " + roleNameStr));

            // 2. NGĂN CHẶN XÓA QUYỀN ADMIN NẾU LÀ ADMIN CUỐI CÙNG (Tùy chọn thêm)
            if (targetRoleName == RoleName.ROLE_ADMIN && userToUpdate.getRoles().contains(role)) {
                long adminCount = userRepository.countByRolesName(RoleName.ROLE_ADMIN);
                if (adminCount <= 1) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Hệ thống phải có ít nhất một Quản trị viên!"
                    ));
                }
            }

            if (userToUpdate.getRoles().contains(role)) {
                userToUpdate.getRoles().remove(role);
            } else {
                userToUpdate.getRoles().add(role);
            }

            userRepository.save(userToUpdate);

            // Trả về dữ liệu đầy đủ để Frontend cập nhật giao diện
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("id", userToUpdate.getId());
            response.put("username", userToUpdate.getUsername());
            response.put("email", userToUpdate.getEmail());
            response.put("fullName", userToUpdate.getFullName());
            response.put("accountLocked", userToUpdate.isAccountLocked());
            response.put("enabled", userToUpdate.isEnabled());
            response.put("roles", userToUpdate.getRoles().stream()
                    .map(r -> r.getName().name())
                    .collect(Collectors.toList()));
            response.put("message", "Cập nhật quyền thành công");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tên quyền không hợp lệ: " + roleNameStr));
        }
    }

    @PutMapping("/users/{id}")
    @Transactional
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        if (isAdminUser(user)) {
            // Admin accounts cannot be disabled or locked
            body.put("enabled", true);
            body.put("accountLocked", false);
        }

        String fullName = (String) body.getOrDefault("fullName", user.getFullName());
        String email = (String) body.getOrDefault("email", user.getEmail());
        String phone = (String) body.getOrDefault("phone", user.getPhone());
        String address = (String) body.getOrDefault("address", user.getAddress());
        Boolean enabled = body.containsKey("enabled") ? Boolean.valueOf(body.get("enabled").toString()) : user.isEnabled();
        Boolean locked = body.containsKey("accountLocked") ? Boolean.valueOf(body.get("accountLocked").toString()) : user.isAccountLocked();

        if (fullName == null || fullName.trim().length() < 2) {
            return ResponseEntity.badRequest().body(Map.of("error", "Họ tên không hợp lệ"));
        }
        if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email không hợp lệ"));
        }

        userRepository.findByEmail(email).ifPresent(existing -> {
            if (!existing.getId().equals(user.getId())) {
                throw new RuntimeException("Email đã được sử dụng bởi tài khoản khác");
            }
        });

        user.setFullName(fullName.trim());
        user.setEmail(email.trim());
        user.setPhone(phone);
        user.setAddress(address);
        user.setEnabled(enabled != null ? enabled : user.isEnabled());
        user.setAccountLocked(locked != null ? locked : user.isAccountLocked());
        if (!user.isAccountLocked()) {
            user.setFailedAttempts(0);
        }

        userRepository.save(user);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
        response.put("fullName", user.getFullName());
        response.put("phone", user.getPhone());
        response.put("address", user.getAddress());
        response.put("enabled", user.isEnabled());
        response.put("accountLocked", user.isAccountLocked());
        response.put("roles", user.getRoles().stream().map(r -> r.getName().name()).toList());

        return ResponseEntity.ok(response);
    }

    private MultipartFile resolveMainImage(MultipartFile imageFile, MultipartFile image) {
        if (imageFile != null && !imageFile.isEmpty()) return imageFile;
        if (image != null && !image.isEmpty()) return image;
        return null;
    }

    private List<MultipartFile> resolveAdditionalFiles(List<MultipartFile> additionalImageFiles, List<MultipartFile> additionalImages) {
        if (additionalImageFiles != null && !additionalImageFiles.isEmpty()) return additionalImageFiles;
        if (additionalImages != null && !additionalImages.isEmpty()) return additionalImages;
        return List.of();
    }

    private void resolveAndSetCategories(Product product, Long categoryId, Long mainCategoryId, Long subCategoryId) {
        // Support new hierarchical categories
        if (subCategoryId != null) {
            Category subCategory = categoryRepository.findById(subCategoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Danh mục phụ không tồn tại"));
            if (subCategory.getParent() == null) {
                throw new IllegalArgumentException("Danh mục phụ không hợp lệ");
            }
            if (mainCategoryId != null && !subCategory.getParent().getId().equals(mainCategoryId)) {
                throw new IllegalArgumentException("Danh mục phụ không thuộc danh mục chính đã chọn");
            }
            product.setSubCategory(subCategory);
            product.setMainCategory(subCategory.getParent());
            product.setCategory(subCategory);
        } else if (mainCategoryId != null) {
            Category mainCategory = categoryRepository.findById(mainCategoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Danh mục chính không tồn tại"));
            if (mainCategory.getParent() != null) {
                throw new IllegalArgumentException("Danh mục chính không hợp lệ");
            }
            product.setMainCategory(mainCategory);
            product.setSubCategory(null);
            product.setCategory(mainCategory);
        } else if (categoryId != null) {
            // Backward compatibility with old category field
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Danh mục không tồn tại"));
            if (category.getParent() != null) {
                product.setSubCategory(category);
                product.setMainCategory(category.getParent());
            } else {
                product.setMainCategory(category);
                product.setSubCategory(null);
            }
            product.setCategory(category); // Keep for backward compatibility
        } else {
            product.setMainCategory(null);
            product.setSubCategory(null);
            product.setCategory(null);
        }
    }
}

