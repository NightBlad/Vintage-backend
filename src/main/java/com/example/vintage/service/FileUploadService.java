package com.example.vintage.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.logging.Logger;

@Service
public class FileUploadService {

    private static final Logger logger = Logger.getLogger(FileUploadService.class.getName());
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final String[] ALLOWED_MIME_TYPES = {
            "image/jpeg", "image/png", "image/webp"
    };

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    /**
     * Validates the uploaded file for MIME type and size
     */
    public boolean isValidImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            return false;
        }

        // Check MIME type
        boolean isValidMimeType = false;
        for (String allowedType : ALLOWED_MIME_TYPES) {
            if (contentType.equals(allowedType)) {
                isValidMimeType = true;
                break;
            }
        }

        if (!isValidMimeType) {
            return false;
        }

        // Check file size
        return file.getSize() <= MAX_FILE_SIZE;
    }

    /**
     * Saves file and returns relative path (products/timestamp_uuid.ext)
     */
    public String saveFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("File is empty");
        }

        // Validate file
        if (!isValidImageFile(file)) {
            throw new IOException("File must be JPG, PNG, or WEBP format and less than 5MB");
        }

        // Create upload directory if not exists
        Path uploadPath = Paths.get(uploadDir, "products");
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Generate unique filename: {timestamp}_{uuid}.{ext}
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String fileExtension = getFileExtension(originalFileName);
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String fileName = timestamp + "_" + uuid + fileExtension;

        // Save file to disk
        Path targetLocation = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        // Return relative path (without leading /)
        return "products/" + fileName;
    }

    /**
     * Deletes file safely - logs error but doesn't throw exception
     */
    public void deleteFile(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return;
        }

        try {
            // Remove leading slash if present
            String cleanPath = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
            Path filePath = Paths.get(uploadDir, cleanPath);
            Files.deleteIfExists(filePath);
            logger.info("Deleted file: " + filePath);
        } catch (IOException e) {
            logger.warning("Could not delete file: " + relativePath + ". Error: " + e.getMessage());
            // Don't throw exception - allow operation to continue
        }
    }

    /**
     * Extracts file extension from filename
     */
    private String getFileExtension(String fileName) {
        if (fileName != null && fileName.lastIndexOf(".") > 0) {
            return fileName.substring(fileName.lastIndexOf("."));
        }
        return "";
    }
}

