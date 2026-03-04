package com.example.vintage.security;

import org.springframework.web.util.HtmlUtils;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

@Component
public class SecurityUtils {

    // Regex patterns cho validation
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{10,11}$");

    // XSS Protection
    public static String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }
        // HTML escape để prevent XSS
        return HtmlUtils.htmlEscape(input.trim());
    }

    // SQL Injection Protection
    public static String sanitizeSqlInput(String input) {
        if (input == null) {
            return null;
        }
        // Remove dangerous SQL characters
        return input.replaceAll("[';\"\\-\\-/\\*\\*/]", "");
    }

    // Validate Username
    public static boolean isValidUsername(String username) {
        return username != null && USERNAME_PATTERN.matcher(username).matches();
    }

    // Validate Strong Password
    public static boolean isStrongPassword(String password) {
        return password != null && PASSWORD_PATTERN.matcher(password).matches();
    }

    // Validate Phone Number
    public static boolean isValidPhone(String phone) {
        return phone == null || phone.isEmpty() || PHONE_PATTERN.matcher(phone).matches();
    }

    // Generate secure error messages (không leak info)
    public static String getSecureErrorMessage(String errorType) {
        switch (errorType) {
            case "invalid_credentials":
                return "Tên đăng nhập hoặc mật khẩu không đúng";
            case "account_locked":
                return "Tài khoản tạm thời bị khóa do nhiều lần đăng nhập sai";
            case "account_disabled":
                return "Tài khoản đã bị vô hiệu hóa";
            case "validation_error":
                return "Dữ liệu không hợp lệ";
            default:
                return "Có lỗi xảy ra, vui lòng thử lại";
        }
    }

    // Mask sensitive data cho logging
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@");
        String localPart = parts[0];
        String domain = parts[1];

        if (localPart.length() <= 2) {
            return "***@" + domain;
        }

        return localPart.substring(0, 2) + "***@" + domain;
    }

    // Rate limiting key generation
    public static String generateRateLimitKey(String clientIP, String endpoint) {
        return clientIP + ":" + endpoint;
    }
}
