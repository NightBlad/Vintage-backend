package com.example.vintage.controller;

import com.example.vintage.entity.User;
import com.example.vintage.entity.Role;
import com.example.vintage.entity.RoleName;
import com.example.vintage.repository.UserRepository;
import com.example.vintage.repository.RoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                           @RequestParam(value = "logout", required = false) String logout,
                           Model model) {
        if (error != null) {
            model.addAttribute("error", "Email hoặc mật khẩu không chính xác!");
        }
        if (logout != null) {
            model.addAttribute("message", "Bạn đã đăng xuất thành công!");
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "auth/register";
    }

    @PostMapping("/register")
    public String registerUser(@RequestParam String username,
                              @RequestParam String email,
                              @RequestParam String password,
                              @RequestParam String confirmPassword,
                              @RequestParam String fullName,
                              @RequestParam(required = false) String phone,
                              @RequestParam(required = false) String address,
                              Model model,
                              RedirectAttributes redirectAttributes) {

        System.out.println("DEBUG: Register POST method called with username: " + username);

        try {
            // 1. USERNAME VALIDATION
            if (username == null || username.trim().isEmpty()) {
                model.addAttribute("error", "Tên đăng nhập không được để trống!");
                return "auth/register";
            }

            if (!username.matches("^[a-zA-Z0-9_]{3,20}$")) {
                model.addAttribute("error", "Tên đăng nhập chỉ chứa chữ cái, số và dấu _ (3-20 ký tự)!");
                return "auth/register";
            }

            // 2. EMAIL VALIDATION
            if (email == null || email.trim().isEmpty()) {
                model.addAttribute("error", "Email không được để trống!");
                return "auth/register";
            }

            if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                model.addAttribute("error", "Email không hợp lệ! Vui lòng nhập đúng định dạng email.");
                return "auth/register";
            }

            // 3. STRONG PASSWORD VALIDATION
            if (password == null || password.trim().isEmpty()) {
                model.addAttribute("error", "Mật khẩu không được để trống!");
                return "auth/register";
            }

            if (password.length() < 8) {
                model.addAttribute("error", "Mật khẩu phải có ít nhất 8 ký tự!");
                return "auth/register";
            }

            // Kiểm tra mật khẩu mạnh: phải có chữ hoa, chữ thường, số và ký tự đặc biệt
            boolean hasUpperCase = password.matches(".*[A-Z].*");
            boolean hasLowerCase = password.matches(".*[a-z].*");
            boolean hasDigit = password.matches(".*\\d.*");
            boolean hasSpecial = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*");

            if (!hasUpperCase) {
                model.addAttribute("error", "Mật khẩu phải chứa ít nhất 1 chữ cái viết hoa!");
                return "auth/register";
            }

            if (!hasLowerCase) {
                model.addAttribute("error", "Mật khẩu phải chứa ít nhất 1 chữ cái viết thường!");
                return "auth/register";
            }

            if (!hasDigit) {
                model.addAttribute("error", "Mật khẩu phải chứa ít nhất 1 chữ số!");
                return "auth/register";
            }

            if (!hasSpecial) {
                model.addAttribute("error", "Mật khẩu phải chứa ít nhất 1 ký tự đặc biệt (!@#$%^&*...)!");
                return "auth/register";
            }

            // 4. CONFIRM PASSWORD VALIDATION
            if (!password.equals(confirmPassword)) {
                model.addAttribute("error", "Mật khẩu xác nhận không khớp!");
                return "auth/register";
            }

            // 5. FULL NAME VALIDATION
            if (fullName == null || fullName.trim().isEmpty()) {
                model.addAttribute("error", "Họ tên không được để trống!");
                return "auth/register";
            }

            if (fullName.trim().length() < 2) {
                model.addAttribute("error", "Họ tên phải có ít nhất 2 ký tự!");
                return "auth/register";
            }

            if (fullName.trim().length() > 50) {
                model.addAttribute("error", "Họ tên không được quá 50 ký tự!");
                return "auth/register";
            }

            // 6. PHONE VALIDATION (nếu có nhập)
            if (phone != null && !phone.trim().isEmpty()) {
                if (!phone.matches("^[0-9]{10,11}$")) {
                    model.addAttribute("error", "Số điện thoại không hợp lệ! Vui lòng nhập 10-11 chữ số.");
                    return "auth/register";
                }
            }

            // 7. ADDRESS VALIDATION (nếu có nhập)
            if (address != null && !address.trim().isEmpty()) {
                if (address.trim().length() > 255) {
                    model.addAttribute("error", "Địa chỉ không được quá 255 ký tự!");
                    return "auth/register";
                }
            }

            // 8. CHECK DUPLICATE USERNAME/EMAIL
            if (userRepository.existsByUsername(username.trim())) {
                model.addAttribute("error", "Tên đăng nhập đã tồn tại! Vui lòng chọn tên khác.");
                return "auth/register";
            }

            if (userRepository.existsByEmail(email.trim())) {
                model.addAttribute("error", "Email đã được sử dụng! Vui lòng sử dụng email khác.");
                return "auth/register";
            }

            // 9. CREATE NEW USER
            User user = new User();
            user.setUsername(username.trim());
            user.setEmail(email.trim().toLowerCase()); // Lưu email dạng lowercase
            user.setPassword(passwordEncoder.encode(password));
            user.setFullName(fullName.trim());
            user.setPhone(phone != null && !phone.trim().isEmpty() ? phone.trim() : null);
            user.setAddress(address != null && !address.trim().isEmpty() ? address.trim() : null);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            user.setEnabled(true);

            // 10. ASSIGN USER ROLE
            Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Role USER không tồn tại trong hệ thống"));

            Set<Role> roles = new HashSet<>();
            roles.add(userRole);
            user.setRoles(roles);

            // 11. SAVE TO DATABASE
            User savedUser = userRepository.save(user);
            System.out.println("DEBUG: User saved successfully with ID: " + savedUser.getId());

            redirectAttributes.addFlashAttribute("success",
                "Đăng ký thành công! Bạn có thể đăng nhập ngay bây giờ với tài khoản: " + username);
            return "redirect:/login";

        } catch (Exception e) {
            System.out.println("DEBUG: Error during registration: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("error", "Có lỗi hệ thống xảy ra! Vui lòng thử lại sau.");
            return "auth/register";
        }
    }
}
