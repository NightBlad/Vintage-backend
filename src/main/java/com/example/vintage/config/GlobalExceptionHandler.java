package com.example.vintage.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for standardized error responses across all endpoints
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle illegal argument exceptions (validation errors, business logic)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Dữ liệu không hợp lệ";
        String code = message.toLowerCase().contains("đã tồn tại") ? "DUPLICATE_RESOURCE" : "BAD_REQUEST";
        return ResponseEntity.badRequest().body(buildErrorResponse(
                code,
                message
        ));
    }

    /**
     * Handle file upload size exceeded
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ResponseEntity<?> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(buildErrorResponse(
                "FILE_TOO_LARGE",
                "File phải là JPG, PNG, WEBP, max 5MB"
        ));
    }

    /**
     * Handle general exceptions
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<?> handleGeneralException(Exception ex) {
        // Log the exception details
        ex.printStackTrace();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "Đã xảy ra lỗi. Vui lòng thử lại sau."
        ));
    }

    /**
     * Handle runtime exceptions
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<?> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.badRequest().body(buildErrorResponse(
                "RUNTIME_ERROR",
                ex.getMessage() != null ? ex.getMessage() : "Lỗi hệ thống"
        ));
    }

    /**
     * Build standardized error response
     */
    private Map<String, Object> buildErrorResponse(String code, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ERROR");
        response.put("code", code);
        response.put("message", message);
        response.put("timestamp", LocalDateTime.now());
        return response;
    }
}

