package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/upload")
public class FileUploadController {

    @Value("${pwj.upload.dir:uploads}")
    private String uploadDir;

    @PostMapping("/image")
    public ResponseEntity<ApiResponse<String>> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Only image files are allowed"));
            }
            if (file.getSize() > 5 * 1024 * 1024) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("File size must be under 5MB"));
            }

            String ext = getExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID() + ext;
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);

            String url = "/api/v1/upload/image/" + filename;
            log.info("Uploaded image: {}", url);
            return ResponseEntity.ok(ApiResponse.ok("Image uploaded", url));

        } catch (IOException e) {
            log.error("Upload failed", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/image/{filename}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        try {
            Path file = Paths.get(uploadDir).resolve(filename).normalize();
            Resource resource = new FileSystemResource(file);
            if (!resource.exists()) return ResponseEntity.notFound().build();

            String contentType = Files.probeContentType(file);
            if (contentType == null) contentType = "image/jpeg";

            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** POST /api/v1/upload/document — Site Engineer uploads PWJ-related docs (PDF, images) */
    @PostMapping("/document")
    public ResponseEntity<ApiResponse<String>> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            String contentType = file.getContentType();
            boolean isAllowed = contentType != null && (
                    contentType.startsWith("image/") ||
                    contentType.equals("application/pdf") ||
                    contentType.equals("application/msword") ||
                    contentType.startsWith("application/vnd.openxmlformats"));
            if (!isAllowed) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Only images, PDF, or Word documents are allowed"));
            }
            if (file.getSize() > 20 * 1024 * 1024) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("File size must be under 20MB"));
            }
            String ext = getExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID() + ext;
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            String url = "/api/v1/upload/document/" + filename;
            log.info("Uploaded document: {}", url);
            return ResponseEntity.ok(ApiResponse.ok("Document uploaded", url));
        } catch (IOException e) {
            log.error("Document upload failed", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/document/{filename}")
    public ResponseEntity<Resource> getDocument(@PathVariable String filename) {
        try {
            Path file = Paths.get(uploadDir).resolve(filename).normalize();
            Resource resource = new FileSystemResource(file);
            if (!resource.exists()) return ResponseEntity.notFound().build();
            String contentType = Files.probeContentType(file);
            if (contentType == null) contentType = "application/octet-stream";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf("."));
    }
}
