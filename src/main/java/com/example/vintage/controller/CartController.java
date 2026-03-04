package com.example.vintage.controller;

import com.example.vintage.entity.Product;
import com.example.vintage.entity.User;
import com.example.vintage.entity.Order;
import com.example.vintage.repository.ProductRepository;
import com.example.vintage.service.CartService;
import com.example.vintage.service.SessionService;
import com.example.vintage.service.OrderService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;
    private final ProductRepository productRepository;
    private final SessionService sessionService;
    private final OrderService orderService;

    public CartController(CartService cartService,
                         ProductRepository productRepository,
                         SessionService sessionService,
                         OrderService orderService) {
        this.cartService = cartService;
        this.productRepository = productRepository;
        this.sessionService = sessionService;
        this.orderService = orderService;
    }

    @GetMapping
    public String viewCart(Model model) {
        Map<Product, Integer> cartItems = cartService.getCartItems();
        double totalAmount = cartService.getTotalAmount();
        int totalItems = cartService.getTotalItems();

        model.addAttribute("cartItems", cartItems);
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("totalItems", totalItems);

        // Thêm thông tin session
        model.addAttribute("isLoggedIn", sessionService.isLoggedIn());
        model.addAttribute("currentUser", sessionService.getCurrentUser());
        model.addAttribute("isAdmin", sessionService.isAdmin());

        return "cart";
    }

    @PostMapping("/add")
    public String addToCart(@RequestParam Long productId,
                           @RequestParam(defaultValue = "1") Integer quantity,
                           RedirectAttributes redirectAttributes) {
        // Kiểm tra đăng nhập trước khi thêm vào giỏ hàng
        if (!sessionService.isLoggedIn()) {
            redirectAttributes.addFlashAttribute("error", "Bạn cần đăng nhập để thêm sản phẩm vào giỏ hàng!");
            return "redirect:/login";
        }

        try {
            Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm"));

            if (!product.isActive()) {
                redirectAttributes.addFlashAttribute("error", "Sản phẩm không khả dụng!");
                return "redirect:/products";
            }

            if (product.getStockQuantity() < quantity) {
                redirectAttributes.addFlashAttribute("error", "Số lượng sản phẩm không đủ!");
                return "redirect:/products/" + productId;
            }

            cartService.addToCart(product, quantity);
            redirectAttributes.addFlashAttribute("success", "Đã thêm sản phẩm vào giỏ hàng!");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra: " + e.getMessage());
        }

        return "redirect:/cart";
    }

    @PostMapping("/update")
    public String updateCart(@RequestParam Long productId,
                            @RequestParam Integer quantity,
                            RedirectAttributes redirectAttributes) {
        try {
            Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm"));

            if (quantity <= 0) {
                cartService.removeFromCart(product);
                redirectAttributes.addFlashAttribute("success", "Đã xóa sản phẩm khỏi giỏ hàng!");
            } else if (product.getStockQuantity() < quantity) {
                redirectAttributes.addFlashAttribute("error", "Số lượng sản phẩm không đủ!");
            } else {
                cartService.updateCart(product, quantity);
                redirectAttributes.addFlashAttribute("success", "Đã cập nhật giỏ hàng!");
            }

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra: " + e.getMessage());
        }

        return "redirect:/cart";
    }

    @PostMapping("/remove")
    public String removeFromCart(@RequestParam Long productId,
                                RedirectAttributes redirectAttributes) {
        try {
            Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm"));

            cartService.removeFromCart(product);
            redirectAttributes.addFlashAttribute("success", "Đã xóa sản phẩm khỏi giỏ hàng!");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra: " + e.getMessage());
        }

        return "redirect:/cart";
    }

    @PostMapping("/clear")
    public String clearCart(RedirectAttributes redirectAttributes) {
        cartService.clearCart();
        redirectAttributes.addFlashAttribute("success", "Đã xóa tất cả sản phẩm trong giỏ hàng!");
        return "redirect:/cart";
    }

    // ===== CHECKOUT FUNCTIONALITY =====
    @GetMapping("/checkout")
    public String checkout(Model model, RedirectAttributes redirectAttributes) {
        // Kiểm tra đăng nhập
        if (!sessionService.isLoggedIn()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để thanh toán!");
            return "redirect:/login";
        }

        // Kiểm tra giỏ hàng có sản phẩm không
        Map<Product, Integer> cartItems = cartService.getCartItems();
        if (cartItems.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Giỏ hàng trống! Vui lòng thêm sản phẩm trước khi thanh toán.");
            return "redirect:/cart";
        }

        // Thông tin giỏ hàng
        double totalAmount = cartService.getTotalAmount();
        int totalItems = cartService.getTotalItems();
        double shippingFee = calculateShippingFee(totalAmount);
        double finalAmount = totalAmount + shippingFee;

        model.addAttribute("cartItems", cartItems);
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("shippingFee", shippingFee);
        model.addAttribute("finalAmount", finalAmount);

        // Thông tin người dùng
        model.addAttribute("currentUser", sessionService.getCurrentUser());
        model.addAttribute("isLoggedIn", sessionService.isLoggedIn());
        model.addAttribute("isAdmin", sessionService.isAdmin());

        return "checkout";
    }

    @PostMapping("/place-order")
    public String placeOrder(@RequestParam String fullName,
                           @RequestParam String phone,
                           @RequestParam String address,
                           @RequestParam(required = false) String notes,
                           @RequestParam String paymentMethod,
                           RedirectAttributes redirectAttributes) {
        // Kiểm tra đăng nhập
        if (!sessionService.isLoggedIn()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để đặt hàng!");
            return "redirect:/login";
        }

        // Kiểm tra giỏ hàng
        Map<Product, Integer> cartItems = cartService.getCartItems();
        if (cartItems.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Giỏ hàng trống!");
            return "redirect:/cart";
        }

        try {
            // Lấy thông tin user hiện tại
            User currentUser = sessionService.getCurrentUser();

            // Debug: Log thông tin user
            System.out.println("Creating order for user: " + currentUser.getUsername() + " (ID: " + currentUser.getId() + ")");

            // Tạo đơn hàng thông qua OrderService
            Order createdOrder = orderService.createOrder(cartItems, fullName, phone, address, notes, paymentMethod, currentUser);

            // Debug: Log thông tin đơn hàng đã tạo
            System.out.println("Order created successfully with ID: " + createdOrder.getId() + ", OrderNumber: " + createdOrder.getOrderNumber());

            // Xóa giỏ hàng sau khi đặt hàng thành công
            cartService.clearCart();

            redirectAttributes.addFlashAttribute("success", "Đặt hàng thành công! Mã đơn hàng: " + createdOrder.getOrderNumber());
            return "redirect:/account/orders";

        } catch (Exception e) {
            // Debug: Log lỗi chi tiết
            System.err.println("Error creating order: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra khi đặt hàng: " + e.getMessage());
            return "redirect:/cart/checkout";
        }
    }

    private double calculateShippingFee(double totalAmount) {
        // Miễn phí ship cho đơn hàng trên 500,000 VND
        if (totalAmount >= 500000) {
            return 0.0;
        }
        // Phí ship cố định 30,000 VND
        return 30000.0;
    }
}
