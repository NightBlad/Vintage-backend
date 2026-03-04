package com.example.vintage.controller;

import com.example.vintage.entity.Category;
import com.example.vintage.entity.Order;
import com.example.vintage.entity.OrderStatus;
import com.example.vintage.entity.Product;
import com.example.vintage.entity.User;
import com.example.vintage.entity.Role;
import com.example.vintage.repository.CategoryRepository;
import com.example.vintage.repository.OrderRepository;
import com.example.vintage.repository.ProductRepository;
import com.example.vintage.repository.UserRepository;
import com.example.vintage.repository.RoleRepository;
import com.example.vintage.service.FileUploadService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final FileUploadService fileUploadService;

    public AdminController(ProductRepository productRepository,
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
    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("totalProducts", productRepository.count());
        model.addAttribute("totalCategories", categoryRepository.count());
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("lowStockProducts", productRepository.findByStockQuantityLessThan(10));
        model.addAttribute("featuredProducts", productRepository.findByActiveTrueAndFeaturedTrue());
        return "admin/dashboard";
    }

    // ===== PRODUCT CRUD =====
    @GetMapping("/products")
    public String listProducts(@RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "10") int size,
                              Model model) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = productRepository.findAll(pageable);

        model.addAttribute("products", products);
        model.addAttribute("currentPage", page);
        return "admin/products/list";
    }

    @GetMapping("/products/add")
    public String showAddProductForm(Model model) {
        model.addAttribute("product", new Product());
        model.addAttribute("categories", categoryRepository.findByActiveTrueOrderByName());
        return "admin/products/form";
    }

    @PostMapping("/products/add")
    public String addProduct(@Valid @ModelAttribute Product product,
                           BindingResult result,
                           @RequestParam("imageFile") MultipartFile imageFile,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        // Validate price range
        if (product.getPrice() != null && product.getPrice().compareTo(new java.math.BigDecimal("9999999999999")) > 0) {
            result.rejectValue("price", "error.product", "Giá không được vượt quá 9,999,999,999,999 VNĐ");
        }
        
        // Validate sale price range
        if (product.getSalePrice() != null && product.getSalePrice().compareTo(new java.math.BigDecimal("9999999999999")) > 0) {
            result.rejectValue("salePrice", "error.product", "Giá khuyến mãi không được vượt quá 9,999,999,999,999 VNĐ");
        }

        // Validate sale price must be less than price
        if (product.getSalePrice() != null && product.getPrice() != null &&
            product.getSalePrice().compareTo(product.getPrice()) >= 0) {
            result.rejectValue("salePrice", "error.product", "Giá khuyến mãi phải nhỏ hơn giá bán");
        }

        // Validate image file if provided
        if (!imageFile.isEmpty() && !fileUploadService.isValidImageFile(imageFile)) {
            result.rejectValue("imageUrl", "error.product", "Vui lòng chọn file ảnh hợp lệ (JPG, PNG, GIF, WEBP)");
        }

        if (result.hasErrors()) {
            model.addAttribute("categories", categoryRepository.findByActiveTrueOrderByName());
            return "admin/products/form";
        }

        try {
            // Handle image upload
            if (!imageFile.isEmpty()) {
                String imagePath = fileUploadService.saveFile(imageFile);
                product.setImageUrl(imagePath);
            }

            productRepository.save(product);
            redirectAttributes.addFlashAttribute("success", "Thêm sản phẩm thành công!");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi upload ảnh: " + e.getMessage());
            model.addAttribute("categories", categoryRepository.findByActiveTrueOrderByName());
            return "admin/products/form";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi thêm sản phẩm: " + e.getMessage());
            model.addAttribute("categories", categoryRepository.findByActiveTrueOrderByName());
            return "admin/products/form";
        }

        return "redirect:/admin/products";
    }

    @GetMapping("/products/edit/{id}")
    public String showEditProductForm(@PathVariable Long id, Model model) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm"));

        model.addAttribute("product", product);
        model.addAttribute("categories", categoryRepository.findByActiveTrueOrderByName());
        return "admin/products/form";
    }

    @PostMapping("/products/edit/{id}")
    public String editProduct(@PathVariable Long id,
                            @Valid @ModelAttribute Product product,
                            BindingResult result,
                            @RequestParam("imageFile") MultipartFile imageFile,
                            Model model,
                            RedirectAttributes redirectAttributes) {

        // Validate price range
        if (product.getPrice() != null && product.getPrice().compareTo(new java.math.BigDecimal("9999999999999")) > 0) {
            result.rejectValue("price", "error.product", "Giá không được vượt quá 9,999,999,999,999 VNĐ");
        }

        // Validate sale price range
        if (product.getSalePrice() != null && product.getSalePrice().compareTo(new java.math.BigDecimal("9999999999999")) > 0) {
            result.rejectValue("salePrice", "error.product", "Giá khuyến mãi không được vượt quá 9,999,999,999,999 VNĐ");
        }

        // Validate sale price must be less than price
        if (product.getSalePrice() != null && product.getPrice() != null &&
            product.getSalePrice().compareTo(product.getPrice()) >= 0) {
            result.rejectValue("salePrice", "error.product", "Giá khuyến mãi phải nhỏ hơn giá bán");
        }

        // Validate image file if provided
        if (!imageFile.isEmpty() && !fileUploadService.isValidImageFile(imageFile)) {
            result.rejectValue("imageUrl", "error.product", "Vui lòng chọn file ảnh hợp lệ (JPG, PNG, GIF, WEBP)");
        }

        if (result.hasErrors()) {
            model.addAttribute("categories", categoryRepository.findByActiveTrueOrderByName());
            return "admin/products/form";
        }

        try {
            // Get existing product to preserve old image if no new image uploaded
            Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm"));

            String oldImagePath = existingProduct.getImageUrl();

            // Handle image upload
            if (!imageFile.isEmpty()) {
                String imagePath = fileUploadService.saveFile(imageFile);
                product.setImageUrl(imagePath);

                // Delete old image if exists
                if (oldImagePath != null && !oldImagePath.isEmpty()) {
                    fileUploadService.deleteFile(oldImagePath);
                }
            } else {
                // Keep existing image if no new image uploaded
                product.setImageUrl(oldImagePath);
            }

            product.setId(id);
            productRepository.save(product);
            redirectAttributes.addFlashAttribute("success", "Cập nhật sản phẩm thành công!");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi upload ảnh: " + e.getMessage());
            model.addAttribute("categories", categoryRepository.findByActiveTrueOrderByName());
            return "admin/products/form";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi cập nhật sản phẩm: " + e.getMessage());
            model.addAttribute("categories", categoryRepository.findByActiveTrueOrderByName());
            return "admin/products/form";
        }

        return "redirect:/admin/products";
    }

    @PostMapping("/products/delete/{id}")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm"));

        product.setActive(false);
        productRepository.save(product);
        redirectAttributes.addFlashAttribute("success", "Xóa sản phẩm thành công!");
        return "redirect:/admin/products";
    }

    // ===== TEST ENDPOINT =====
    @GetMapping("/test")
    @ResponseBody
    public String test() {
        return "Admin Controller is working!";
    }

    // ===== CATEGORY CRUD =====
    @GetMapping("/categories")
    public String listCategories(Model model) {
        List<Category> categories = categoryRepository.findAll();
        model.addAttribute("categories", categories);
        return "admin/categories/list";
    }

    @GetMapping("/categories/add")
    public String showAddCategoryForm(Model model) {
        model.addAttribute("category", new Category());
        return "admin/categories/form";
    }

    @PostMapping("/categories/add")
    public String addCategory(@Valid @ModelAttribute Category category,
                            BindingResult result,
                            RedirectAttributes redirectAttributes) {

        // Kiểm tra trùng tên danh mục
        if (categoryRepository.existsByName(category.getName())) {
            result.rejectValue("name", "error.category", "Tên danh mục đã tồn tại");
        }

        if (result.hasErrors()) {
            return "admin/categories/form";
        }

        // Đảm bảo danh mục mới được kích hoạt
        category.setActive(true);
        categoryRepository.save(category);
        redirectAttributes.addFlashAttribute("success", "Thêm danh mục thành công!");
        return "redirect:/admin/categories";
    }

    @GetMapping("/categories/edit/{id}")
    public String showEditCategoryForm(@PathVariable Long id, Model model) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));

        model.addAttribute("category", category);
        return "admin/categories/form";
    }

    @PostMapping("/categories/edit/{id}")
    public String editCategory(@PathVariable Long id,
                             @Valid @ModelAttribute Category category,
                             BindingResult result,
                             RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "admin/categories/form";
        }

        category.setId(id);
        categoryRepository.save(category);
        redirectAttributes.addFlashAttribute("success", "Cập nhật danh mục thành công!");
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/delete/{id}")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));

        category.setActive(false);
        categoryRepository.save(category);
        redirectAttributes.addFlashAttribute("success", "Xóa danh mục thành công!");
        return "redirect:/admin/categories";
    }

    // ===== ORDER MANAGEMENT =====
    @GetMapping("/orders")
    public String listOrders(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "10") int size,
                            @RequestParam(required = false) OrderStatus status,
                            Model model) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders;

        if (status != null) {
            orders = orderRepository.findByStatusOrderByOrderDateDesc(status, pageable);
        } else {
            orders = orderRepository.findAllOrderByOrderDateDesc(pageable);
        }

        model.addAttribute("orders", orders);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("orderStatuses", OrderStatus.values());
        model.addAttribute("currentPage", page);
        return "admin/orders/list";
    }

    @GetMapping("/orders/{id}")
    public String viewOrder(@PathVariable Long id, Model model) {
        Order order = orderRepository.findByIdWithItems(id)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        model.addAttribute("order", order);
        model.addAttribute("orderStatuses", OrderStatus.values());
        return "admin/orders/detail";
    }

    @PostMapping("/orders/{id}/status")
    public String updateOrderStatus(@PathVariable Long id,
                                   @RequestParam OrderStatus status,
                                   RedirectAttributes redirectAttributes) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        order.setStatus(status);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        redirectAttributes.addFlashAttribute("success", "Cập nhật trạng thái đơn hàng thành công!");
        return "redirect:/admin/orders/" + id;
    }

    // ===== USER MANAGEMENT =====
    @GetMapping("/users")
    public String listUsers(@RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "10") int size,
                           Model model) {
        Pageable pageable = PageRequest.of(page, size);
        Page<User> users = userRepository.findAll(pageable);

        model.addAttribute("users", users);
        model.addAttribute("currentPage", page);
        return "admin/users/list";
    }

    @GetMapping("/users/{id}")
    public String viewUser(@PathVariable Long id, Model model) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        model.addAttribute("user", user);
        model.addAttribute("roles", roleRepository.findAll());
        return "admin/users/detail";
    }

    @PostMapping("/users/{id}/toggle-status")
    public String toggleUserStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        user.setEnabled(!user.isEnabled());
        userRepository.save(user);

        String status = user.isEnabled() ? "kích hoạt" : "vô hiệu hóa";
        redirectAttributes.addFlashAttribute("success", "Đã " + status + " tài khoản thành công!");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/update-roles")
    public String updateUserRoles(@PathVariable Long id,
                                  @RequestParam(required = false) List<Long> roleIds,
                                  RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        user.getRoles().clear();
        if (roleIds != null && !roleIds.isEmpty()) {
            List<Role> roles = roleRepository.findAllById(roleIds);
            user.getRoles().addAll(roles);
        }

        userRepository.save(user);
        redirectAttributes.addFlashAttribute("success", "Cập nhật quyền người dùng thành công!");
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        // Không cho phép xóa tài khoản admin
        boolean isAdmin = user.getRoles().stream()
            .anyMatch(role -> "ADMIN".equals(role.getName().name()));

        if (isAdmin) {
            redirectAttributes.addFlashAttribute("error", "Không thể xóa tài khoản Admin!");
            return "redirect:/admin/users";
        }

        user.setEnabled(false);
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("success", "Xóa người dùng thành công!");
        return "redirect:/admin/users";
    }
}
