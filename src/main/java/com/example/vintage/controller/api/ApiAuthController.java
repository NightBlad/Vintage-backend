package com.example.vintage.controller.api;

import com.example.vintage.entity.Role;
import com.example.vintage.entity.RoleName;
import com.example.vintage.entity.User;
import com.example.vintage.repository.RoleRepository;
import com.example.vintage.repository.UserRepository;
import com.example.vintage.service.CartService;
import com.example.vintage.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping({"/api/auth", "/api/v1/auth"})
public class ApiAuthController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SessionService sessionService;
    private final CartService cartService;

    public ApiAuthController(UserRepository userRepository,
                             RoleRepository roleRepository,
                             PasswordEncoder passwordEncoder,
                              AuthenticationManager authenticationManager,
                              SessionService sessionService,
                              CartService cartService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.sessionService = sessionService;
        this.cartService = cartService;
    }

    /**
     * POST /api/auth/login
     * Body: { "username": "...", "password": "..." }
     * Trả về thông tin user và set session cookie
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Vui lòng nhập tên đăng nhập và mật khẩu"));
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            // Lưu authentication vào SecurityContext và session
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            HttpSession session = request.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

            User user = sessionService.getCurrentUser();
            cartService.clearCart(); // reset cart when switching account within same browser session
            return ResponseEntity.ok(buildUserResponse(user));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Tên đăng nhập hoặc mật khẩu không đúng"));
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Đăng nhập thất bại: " + e.getMessage()));
        }
    }

    /**
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        cartService.clearCart();
        return ResponseEntity.ok(Map.of("message", "Đăng xuất thành công"));
    }

    /**
     * GET /api/auth/me
     * Lấy thông tin user hiện tại từ session
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        User user = sessionService.getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Chưa đăng nhập"));
        }
        return ResponseEntity.ok(buildUserResponse(user));
    }

    /**
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String email = body.get("email");
        String password = body.get("password");
        String confirmPassword = body.get("confirmPassword");
        String fullName = body.get("fullName");
        String phone = body.getOrDefault("phone", "");
        String address = body.getOrDefault("address", "");

        // Validate
        if (username == null || !username.matches("^[a-zA-Z0-9_]{3,20}$")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Tên đăng nhập chỉ chứa chữ cái, số và dấu _ (3-20 ký tự)"));
        }
        if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email không hợp lệ"));
        }
        if (password == null || password.length() < 8) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Mật khẩu phải có ít nhất 8 ký tự"));
        }
        if (!password.matches(".*[A-Z].*")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Mật khẩu phải chứa ít nhất 1 chữ hoa"));
        }
        if (!password.matches(".*[a-z].*")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Mật khẩu phải chứa ít nhất 1 chữ thường"));
        }
        if (!password.matches(".*\\d.*")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Mật khẩu phải chứa ít nhất 1 chữ số"));
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Mật khẩu phải chứa ít nhất 1 ký tự đặc biệt"));
        }
        if (!password.equals(confirmPassword)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mật khẩu xác nhận không khớp"));
        }
        if (fullName == null || fullName.trim().length() < 2) {
            return ResponseEntity.badRequest().body(Map.of("error", "Họ tên không hợp lệ"));
        }
        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tên đăng nhập đã tồn tại"));
        }
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email đã được đăng ký"));
        }

        // Tạo user
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setFullName(fullName.trim());
        user.setPhone(phone);
        user.setAddress(address);
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Role USER không tồn tại"));
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        user.setRoles(roles);

        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Đăng ký thành công! Vui lòng đăng nhập."));
    }

    private Map<String, Object> buildUserResponse(User user) {
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(r -> r.getName() == RoleName.ROLE_ADMIN);
        return Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "fullName", user.getFullName(),
                "phone", user.getPhone() != null ? user.getPhone() : "",
                "address", user.getAddress() != null ? user.getAddress() : "",
                "roles", user.getRoles().stream().map(r -> r.getName().name()).toList(),
                "isAdmin", isAdmin
        );
    }
}

