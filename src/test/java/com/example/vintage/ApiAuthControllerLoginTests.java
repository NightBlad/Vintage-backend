package com.example.vintage;

import com.example.vintage.controller.api.ApiAuthController;
import com.example.vintage.entity.Role;
import com.example.vintage.entity.RoleName;
import com.example.vintage.entity.User;
import com.example.vintage.repository.RoleRepository;
import com.example.vintage.repository.UserRepository;
import com.example.vintage.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class ApiAuthControllerLoginTests {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private PasswordEncoder passwordEncoder;
    private AuthenticationManager authenticationManager;
    private SessionService sessionService;
    private ApiAuthController apiAuthController;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authenticationManager = mock(AuthenticationManager.class);
        sessionService = mock(SessionService.class);
        apiAuthController = new ApiAuthController(userRepository, roleRepository, passwordEncoder, authenticationManager, sessionService);
    }

    @Test // Test đăng nhập với tài khoản ROLE_USER -> phải thành công
    void testLogin_WithUserRole_ShouldSuccess() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "user1");
        body.put("password", "password");

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(true)).thenReturn(session);

        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);

        User user = buildUser("user1", RoleName.ROLE_USER);
        when(sessionService.getCurrentUser()).thenReturn(user);

        ResponseEntity<?> result = apiAuthController.login(body, request, response);

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> resBody = (Map<?, ?>) result.getBody();
        assertThat(resBody.get("username")).isEqualTo("user1");
        assertThat(resBody.get("isAdmin")).isEqualTo(false);
    }

    @Test // Test đăng nhập với tài khoản ROLE_ADMIN -> phải thành công và isAdmin = true
    void testLogin_WithAdminRole_ShouldSuccess() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "admin");
        body.put("password", "password");

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(true)).thenReturn(session);

        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);

        User adminUser = buildUser("admin", RoleName.ROLE_ADMIN);
        when(sessionService.getCurrentUser()).thenReturn(adminUser);

        ResponseEntity<?> result = apiAuthController.login(body, request, response);

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> resBody = (Map<?, ?>) result.getBody();
        assertThat(resBody.get("username")).isEqualTo("admin");
        assertThat(resBody.get("isAdmin")).isEqualTo(true);
    }

    @Test // Test đăng nhập với mật khẩu sai -> phải trả về 401 và thông báo lỗi
    void testLogin_WithWrongPassword_ShouldFail() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "user1");
        body.put("password", "wrong");

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        ResponseEntity<?> result = apiAuthController.login(body, request, response);

        assertThat(result.getStatusCode().value()).isEqualTo(401);
        Map<?, ?> resBody = (Map<?, ?>) result.getBody();
        assertThat(resBody.get("error")).isEqualTo("Tên đăng nhập hoặc mật khẩu không đúng");
    }

    @Test // Test đăng nhập với username không tồn tại -> cũng phải thất bại (401)
    void testLogin_WithNonExistingUsername_ShouldFail() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "unknown");
        body.put("password", "password");

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        ResponseEntity<?> result = apiAuthController.login(body, request, response);

        assertThat(result.getStatusCode().value()).isEqualTo(401);
        Map<?, ?> resBody = (Map<?, ?>) result.getBody();
        assertThat(resBody.get("error")).isEqualTo("Tên đăng nhập hoặc mật khẩu không đúng");
    }

    @Test // Test đăng nhập với tài khoản bị khóa -> giả lập bằng cách AuthenticationManager ném BadCredentials hoặc custom -> vẫn phải 401
    void testLogin_WithLockedAccount_ShouldFail() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "lockedUser");
        body.put("password", "password");

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        // Ở tầng controller hiện tại không phân biệt locked/không locked,
        // nên test này vẫn giả lập thất bại xác thực và mong đợi 401.
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("User is locked"));

        ResponseEntity<?> result = apiAuthController.login(body, request, response);

        assertThat(result.getStatusCode().value()).isEqualTo(401);
        Map<?, ?> resBody = (Map<?, ?>) result.getBody();
        assertThat(resBody.get("error")).isEqualTo("Tên đăng nhập hoặc mật khẩu không đúng");
    }

    // Hàm tiện ích dựng User với role
    private User buildUser(String username, RoleName roleName) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName("Test " + username);
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        Role role = new Role();
        role.setName(roleName);
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);

        return user;
    }
}

