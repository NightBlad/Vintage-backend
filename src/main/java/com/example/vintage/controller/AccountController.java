package com.example.vintage.controller;

import com.example.vintage.entity.User;
import com.example.vintage.entity.Order;
import com.example.vintage.repository.UserRepository;
import com.example.vintage.service.SessionService;
import com.example.vintage.service.OrderService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import java.util.List;

@Controller
@RequestMapping("/account")
public class AccountController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final OrderService orderService;

    public AccountController(UserRepository userRepository, PasswordEncoder passwordEncoder, SessionService sessionService, OrderService orderService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.orderService = orderService;
    }

    @GetMapping("/profile")
    public String showProfile(Model model) {
        // Kiểm tra đăng nhập
        if (!sessionService.isLoggedIn()) {
            return "redirect:/login";
        }

        User currentUser = sessionService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("user", currentUser);
        model.addAttribute("title", "Thông tin cá nhân");
        model.addAttribute("isLoggedIn", true);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("isAdmin", sessionService.isAdmin());

        return "account/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@Valid @ModelAttribute User user,
                              BindingResult result,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        // Kiểm tra đăng nhập
        if (!sessionService.isLoggedIn()) {
            return "redirect:/login";
        }

        User currentUser = sessionService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        if (result.hasErrors()) {
            model.addAttribute("title", "Thông tin cá nhân");
            model.addAttribute("isLoggedIn", true);
            model.addAttribute("currentUser", currentUser);
            model.addAttribute("isAdmin", sessionService.isAdmin());
            return "account/profile";
        }

        // Cập nhật thông tin (giữ nguyên username, password và role)
        currentUser.setFullName(user.getFullName());
        currentUser.setEmail(user.getEmail());
        currentUser.setPhone(user.getPhone());
        currentUser.setAddress(user.getAddress());

        userRepository.save(currentUser);

        // Cập nhật session
        sessionService.updateCurrentUser(currentUser);

        redirectAttributes.addFlashAttribute("success", "Cập nhật thông tin thành công!");
        return "redirect:/account/profile";
    }

    @GetMapping("/change-password")
    public String showChangePasswordForm(Model model) {
        // Kiểm tra đăng nhập
        if (!sessionService.isLoggedIn()) {
            return "redirect:/login";
        }

        User currentUser = sessionService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("title", "Đổi mật khẩu");
        model.addAttribute("isLoggedIn", true);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("isAdmin", sessionService.isAdmin());

        return "account/change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam("currentPassword") String currentPassword,
                               @RequestParam("newPassword") String newPassword,
                               @RequestParam("confirmPassword") String confirmPassword,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        // Kiểm tra đăng nhập
        if (!sessionService.isLoggedIn()) {
            return "redirect:/login";
        }

        User currentUser = sessionService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        // Kiểm tra mật khẩu hiện tại
        if (!passwordEncoder.matches(currentPassword, currentUser.getPassword())) {
            model.addAttribute("error", "Mật khẩu hiện tại không đúng!");
            model.addAttribute("title", "Đổi mật khẩu");
            model.addAttribute("isLoggedIn", true);
            model.addAttribute("currentUser", currentUser);
            model.addAttribute("isAdmin", sessionService.isAdmin());
            return "account/change-password";
        }

        // Kiểm tra mật khẩu mới và xác nhận
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "Mật khẩu mới và xác nhận mật khẩu không khớp!");
            model.addAttribute("title", "Đổi mật khẩu");
            model.addAttribute("isLoggedIn", true);
            model.addAttribute("currentUser", currentUser);
            model.addAttribute("isAdmin", sessionService.isAdmin());
            return "account/change-password";
        }

        // Kiểm tra độ dài mật khẩu
        if (newPassword.length() < 6) {
            model.addAttribute("error", "Mật khẩu mới phải có ít nhất 6 ký tự!");
            model.addAttribute("title", "Đổi mật khẩu");
            model.addAttribute("isLoggedIn", true);
            model.addAttribute("currentUser", currentUser);
            model.addAttribute("isAdmin", sessionService.isAdmin());
            return "account/change-password";
        }

        // Cập nhật mật khẩu
        currentUser.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(currentUser);

        // Cập nhật session
        sessionService.updateCurrentUser(currentUser);

        redirectAttributes.addFlashAttribute("success", "Đổi mật khẩu thành công!");
        return "redirect:/account/profile";
    }

    @GetMapping("/orders")
    public String showOrders(Model model) {
        // Kiểm tra đăng nhập
        if (!sessionService.isLoggedIn()) {
            return "redirect:/login";
        }

        User currentUser = sessionService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        // Debug: Log thông tin user
        System.out.println("Loading orders for user: " + currentUser.getUsername() + " (ID: " + currentUser.getId() + ")");

        // Debug: Kiểm tra tổng số đơn hàng trong database
        List<Order> allOrders = orderService.findAllOrders();
        System.out.println("Total orders in database: " + allOrders.size());

        // Lấy danh sách đơn hàng của user
        List<Order> orders = orderService.findOrdersByUser(currentUser);

        // Debug: Log số lượng đơn hàng tìm thấy
        System.out.println("Found " + orders.size() + " orders for user: " + currentUser.getUsername());
        if (!orders.isEmpty()) {
            for (Order order : orders) {
                System.out.println("Order ID: " + order.getId() + ", OrderNumber: " + order.getOrderNumber() + ", Status: " + order.getStatus() + ", User ID: " + order.getUser().getId());
            }
        } else {
            System.out.println("No orders found for this user. Checking all orders...");
            for (Order order : allOrders) {
                System.out.println("All Order - ID: " + order.getId() + ", User ID: " + order.getUser().getId() + ", Current User ID: " + currentUser.getId());
            }
        }

        model.addAttribute("orders", orders);
        model.addAttribute("title", "Đơn hàng của tôi");
        model.addAttribute("isLoggedIn", true);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("isAdmin", sessionService.isAdmin());

        return "account/orders";
    }

    @GetMapping("/orders/debug")
    public String debugOrders(Model model) {
        // Kiểm tra đăng nhập
        if (!sessionService.isLoggedIn()) {
            return "redirect:/login";
        }

        User currentUser = sessionService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        // Lấy tất cả đơn hàng và hiển thị thông tin debug
        List<Order> allOrders = orderService.findAllOrders();

        StringBuilder debugInfo = new StringBuilder();
        debugInfo.append("Current User ID: ").append(currentUser.getId()).append("\n");
        debugInfo.append("Current Username: ").append(currentUser.getUsername()).append("\n");
        debugInfo.append("Total orders in database: ").append(allOrders.size()).append("\n\n");

        for (Order order : allOrders) {
            debugInfo.append("Order ID: ").append(order.getId())
                    .append(", User ID: ").append(order.getUser().getId())
                    .append(", Username: ").append(order.getUser().getUsername())
                    .append(", Order Number: ").append(order.getOrderNumber())
                    .append(", Match: ").append(order.getUser().getId().equals(currentUser.getId()))
                    .append("\n");
        }

        model.addAttribute("debugInfo", debugInfo.toString());
        model.addAttribute("title", "Debug Orders");
        model.addAttribute("isLoggedIn", true);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("isAdmin", sessionService.isAdmin());

        return "account/debug-orders";
    }

    @GetMapping("/addresses")
    public String showAddresses(Model model) {
        // Kiểm tra đăng nhập
        if (!sessionService.isLoggedIn()) {
            return "redirect:/login";
        }

        User currentUser = sessionService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("title", "Địa chỉ giao hàng");
        model.addAttribute("isLoggedIn", true);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("isAdmin", sessionService.isAdmin());

        return "account/addresses";
    }
}
