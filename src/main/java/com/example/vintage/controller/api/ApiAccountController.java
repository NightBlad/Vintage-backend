package com.example.vintage.controller.api;

import com.example.vintage.entity.User;
import com.example.vintage.repository.UserRepository;
import com.example.vintage.service.SessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/account", "/api/v1/account"})
public class ApiAccountController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;

    public ApiAccountController(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                SessionService sessionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
    }

    /**
     * GET /api/account/profile
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        User user = sessionService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Chưa đăng nhập"));
        return ResponseEntity.ok(toUserProfile(user));
    }

    /**
     * PUT /api/account/profile
     * Body: { "fullName": "...", "email": "...", "phone": "...", "address": "..." }
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> body) {
        User user = sessionService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Chưa đăng nhập"));

        String fullName = body.get("fullName");
        String email = body.get("email");
        String phone = body.getOrDefault("phone", "");
        String address = body.getOrDefault("address", "");

        if (fullName == null || fullName.trim().length() < 2) {
            return ResponseEntity.badRequest().body(Map.of("error", "Họ tên không hợp lệ"));
        }
        if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email không hợp lệ"));
        }

        // Kiểm tra email đã tồn tại chưa (trừ của chính mình)
        userRepository.findByEmail(email).ifPresent(existing -> {
            if (!existing.getId().equals(user.getId())) {
                throw new RuntimeException("Email đã được sử dụng bởi tài khoản khác");
            }
        });

        user.setFullName(fullName.trim());
        user.setEmail(email.trim());
        user.setPhone(phone);
        user.setAddress(address);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "Cập nhật thông tin thành công",
                "profile", toUserProfile(user)
        ));
    }

    /**
     * PUT /api/account/change-password
     * Body: { "currentPassword": "...", "newPassword": "...", "confirmPassword": "..." }
     */
    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body) {
        User user = sessionService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Chưa đăng nhập"));

        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");
        String confirmPassword = body.get("confirmPassword");

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mật khẩu hiện tại không đúng"));
        }
        if (newPassword == null || newPassword.length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mật khẩu mới phải có ít nhất 8 ký tự"));
        }
        if (!newPassword.equals(confirmPassword)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mật khẩu xác nhận không khớp"));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công"));
    }

    private Map<String, Object> toUserProfile(User u) {
        return Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "email", u.getEmail(),
                "fullName", u.getFullName(),
                "phone", u.getPhone() != null ? u.getPhone() : "",
                "address", u.getAddress() != null ? u.getAddress() : "",
                "createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : ""
        );
    }
}

