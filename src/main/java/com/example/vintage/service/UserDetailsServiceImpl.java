package com.example.vintage.service;

import com.example.vintage.entity.User;
import com.example.vintage.repository.UserRepository;
import com.example.vintage.security.LoginAttemptService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.stream.Collectors;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final LoginAttemptService loginAttemptService;

    public UserDetailsServiceImpl(UserRepository userRepository, LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        // 1. Tìm user theo username hoặc email
        User user = userRepository.findByUsername(usernameOrEmail)
                .orElseGet(() -> userRepository.findByEmail(usernameOrEmail)
                        .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng: " + usernameOrEmail)));

        // 2. KIỂM TRA TRẠNG THÁI KHÓA
        // Kiểm tra logic tự động khóa do nhập sai mật khẩu (LoginAttemptService)
        if (loginAttemptService.isAccountLocked(user.getUsername())) {
            long remainingTime = loginAttemptService.getRemainingLockoutMinutes(user.getUsername());
            throw new LockedException("Tài khoản đã bị khóa tạm thời. Vui lòng thử lại sau " + remainingTime + " phút.");
        }

        // Kiểm tra trạng thái khóa thủ công từ Admin (trường accountLocked trong DB)
        if (user.isAccountLocked()) {
            throw new LockedException("Tài khoản này đã bị quản trị viên khóa.");
        }

        Collection<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toList());

        // 3. TRUYỀN GIÁ TRỊ THỰC TẾ VÀO BUILDER
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(user.isAccountLocked()) // Sử dụng giá trị thật từ Database
                .credentialsExpired(false)
                .disabled(!user.isEnabled())
                .build();
    }
}
