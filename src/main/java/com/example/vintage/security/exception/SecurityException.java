package com.example.vintage.security.exception;

public class SecurityException extends RuntimeException {

    private final String userMessage;
    private final String logMessage;

    public SecurityException(String userMessage, String logMessage) {
        super(logMessage);
        this.userMessage = userMessage;
        this.logMessage = logMessage;
    }

    public SecurityException(String userMessage, String logMessage, Throwable cause) {
        super(logMessage, cause);
        this.userMessage = userMessage;
        this.logMessage = logMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getLogMessage() {
        return logMessage;
    }
}

class AccountLockedException extends SecurityException {
    public AccountLockedException(String username) {
        super("Tài khoản tạm thời bị khóa do nhiều lần đăng nhập sai",
              "Account locked for user: " + username);
    }
}

class InvalidCredentialsException extends SecurityException {
    public InvalidCredentialsException(String username) {
        super("Tên đăng nhập hoặc mật khẩu không đúng",
              "Invalid login attempt for user: " + username);
    }
}

class ValidationException extends SecurityException {
    public ValidationException(String field, String value) {
        super("Dữ liệu không hợp lệ",
              "Validation failed for field: " + field + ", value: " + value);
    }
}
