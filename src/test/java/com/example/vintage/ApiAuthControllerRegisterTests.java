package com.example.vintage;

import com.example.vintage.controller.api.ApiAuthController;
import com.example.vintage.entity.Role;
import com.example.vintage.entity.RoleName;
import com.example.vintage.entity.User;
import com.example.vintage.repository.RoleRepository;
import com.example.vintage.repository.UserRepository;
import com.example.vintage.service.CartService;
import com.example.vintage.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class ApiAuthControllerRegisterTests {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private PasswordEncoder passwordEncoder;
    private AuthenticationManager authenticationManager;
    private SessionService sessionService;
    private CartService cartService;
    private ApiAuthController apiAuthController;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authenticationManager = mock(AuthenticationManager.class);
        sessionService = mock(SessionService.class);
        cartService = mock(CartService.class);

        // Mock ROLE_USER to avoid RuntimeException("Role USER không tồn tại") in register()
        Role userRole = new Role();
        userRole.setName(RoleName.ROLE_USER);
        when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(userRole));

        apiAuthController = new ApiAuthController(userRepository, roleRepository, passwordEncoder, authenticationManager, sessionService, cartService);
    }



    @Test // Test đăng ký với thông tin hợp lệ -> phải tạo được tài khoản (trả về 200 và lưu user)
    void testRegister_WithValidInformation_ShouldCreateAccount() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "newuser");
        body.put("email", "newuser@example.com");
        body.put("password", "Password1!");
        body.put("confirmPassword", "Password1!");
        body.put("fullName", "New User");
        body.put("phone", "0123456789");
        body.put("address", "123 Street");


        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1!")).thenReturn("ENCODED");

        ResponseEntity<?> response = apiAuthController.register(body);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(((Map<?, ?>) response.getBody()).get("message")).isEqualTo("Đăng ký thành công! Vui lòng đăng nhập.");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("newuser");
        assertThat(savedUser.getEmail()).isEqualTo("newuser@example.com");
    }

    @Test // Test đăng ký với username đã tồn tại -> phải báo lỗi "Tên đăng nhập đã tồn tại" và không lưu user
    void testRegister_WithExistingUsername_ShouldFail() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "existinguser");
        body.put("email", "unique@example.com");
        body.put("password", "Password1!");
        body.put("confirmPassword", "Password1!");
        body.put("fullName", "Existing User");

        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        ResponseEntity<?> response = apiAuthController.register(body);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(((Map<?, ?>) response.getBody()).get("error")).isEqualTo("Tên đăng nhập đã tồn tại");
        verify(userRepository, never()).save(any());

    }

    @Test // Test đăng ký với email đã tồn tại -> phải báo lỗi "Email đã được đăng ký" và không lưu user
    void testRegister_WithExistingEmail_ShouldFail() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "uniqueuser");
        body.put("email", "existing@example.com");
        body.put("password", "Password1!");
        body.put("confirmPassword", "Password1!");
        body.put("fullName", "Existing Email");

        when(userRepository.existsByUsername("uniqueuser")).thenReturn(false);
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        ResponseEntity<?> response = apiAuthController.register(body);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(((Map<?, ?>) response.getBody()).get("error")).isEqualTo("Email đã được đăng ký");
        verify(userRepository, never()).save(any());

    }



    @Test // Test đăng ký khi mật khẩu và mật khẩu xác nhận không khớp -> phải báo lỗi "Mật khẩu xác nhận không khớp"
    void testRegister_WithPasswordMismatch_ShouldFail() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "userpass");
        body.put("email", "userpass@example.com");
        body.put("password", "Password1!");
        body.put("confirmPassword", "Password2!");
        body.put("fullName", "User Pass");

        ResponseEntity<?> response = apiAuthController.register(body);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(((Map<?, ?>) response.getBody()).get("error")).isEqualTo("Mật khẩu xác nhận không khớp");
        verify(userRepository, never()).save(any());

    }

    @Test // Test đăng ký với email sai định dạng -> phải báo lỗi "Email không hợp lệ" và không lưu user
    void testRegister_WithInvalidEmailFormat_ShouldFail() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "useremail");
        body.put("email", "invalid-email");
        body.put("password", "Password1!");
        body.put("confirmPassword", "Password1!");
        body.put("fullName", "User Email");

        ResponseEntity<?> response = apiAuthController.register(body);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(((Map<?, ?>) response.getBody()).get("error")).isEqualTo("Email không hợp lệ");
        verify(userRepository, never()).save(any());
    }
}