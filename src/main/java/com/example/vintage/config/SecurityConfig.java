package com.example.vintage.config;

import com.example.vintage.service.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationProvider authenticationProvider) {
        return new ProviderManager(authenticationProvider);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:4200"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true); // Cho phép gửi cookie/session
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable()) // Disable CSRF vì frontend Angular dùng cookie/session
            .authenticationProvider(authenticationProvider())
            .authorizeHttpRequests(authz -> authz
                // Public API endpoints
                .requestMatchers(
                    "/api/auth/**",
                    "/api/v1/auth/**",
                    "/api/products/**",
                    "/api/v1/products/**",
                    "/api/categories/**",
                    "/api/v1/categories/**",
                    "/api/search",
                    "/api/v1/search",
                    "/api/chatbot/**",
                    "/api/v1/chatbot/**",
                    "/uploads/**",
                    "/h2-console/**"
                ).permitAll()
                // User management endpoints: ADMIN only
                .requestMatchers("/api/admin/users/**", "/api/v1/admin/users/**", "/admin/users/**").hasRole("ADMIN")
                // Admin/Staff endpoints
                .requestMatchers("/api/admin/**", "/api/v1/admin/**").hasAnyRole("ADMIN", "STAFF")
                // Inventory management endpoints (admin & staff)
                .requestMatchers("/api/inventory/**", "/api/v1/inventory/**").hasAnyRole("ADMIN", "STAFF")
                // Authenticated user endpoints
                .requestMatchers(
                    "/api/account/**", "/api/v1/account/**",
                    "/api/cart/**", "/api/v1/cart/**",
                    "/api/orders/**", "/api/v1/orders/**"
                ).hasAnyRole("USER", "ADMIN", "STAFF")
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                // Trả JSON 401 thay vì redirect về login page
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    new ObjectMapper().writeValue(response.getWriter(),
                        Map.of("error", "Unauthorized", "message", "Vui lòng đăng nhập để tiếp tục"));
                })
                // Trả JSON 403 thay vì trang lỗi
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    new ObjectMapper().writeValue(response.getWriter(),
                        Map.of("error", "Forbidden", "message", "Bạn không có quyền truy cập tài nguyên này"));
                })
            )
            .sessionManagement(session -> session
                .sessionFixation().changeSessionId()
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
            )
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin())
            );

        return http.build();
    }
}
