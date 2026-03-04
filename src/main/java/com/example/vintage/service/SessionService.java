package com.example.vintage.service;

import com.example.vintage.entity.User;
import com.example.vintage.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    private final UserRepository userRepository;

    public SessionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void login(User user) {
        // Custom login logic nếu cần
        // Spring Security sẽ handle authentication
    }

    public void logout() {
        SecurityContextHolder.clearContext();
    }

    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            // Thử tìm theo username trước, nếu không có thì tìm theo email
            return userRepository.findByUsername(auth.getName())
                    .or(() -> userRepository.findByEmail(auth.getName()))
                    .orElse(null);
        }
        return null;
    }

    public boolean isLoggedIn() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser");
    }

    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getAuthorities().stream()
                    .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));
        }
        return false;
    }

    public String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            return auth.getName();
        }
        return null;
    }

    public String getCurrentFullName() {
        User user = getCurrentUser();
        return user != null ? user.getFullName() : null;
    }

    public int getCartItemCount() {
        // Đây là placeholder - bạn có thể implement logic thực tế sau
        // Hiện tại return 0 để tránh lỗi
        return 0;
    }

    public void updateCurrentUser(User user) {
        // Method này được gọi sau khi cập nhật thông tin user
        // Trong Spring Security, thông tin user được lưu trong SecurityContext
        // không cần cập nhật gì thêm vì getCurrentUser() sẽ lấy từ database
        // Method này chỉ để compatibility với code hiện tại
    }
}
