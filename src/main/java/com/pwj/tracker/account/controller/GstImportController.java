package com.pwj.tracker.account.controller;

import com.pwj.tracker.account.dto.GstImportBatchDto;
import com.pwj.tracker.account.entity.GstImportBatch;
import com.pwj.tracker.account.service.GstImportService;
import com.pwj.tracker.dto.ApiResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** GSTR-2B excel import for the GST tab — see GstImportService for the matching rules. */
@RestController
@RequestMapping("/api/v1/gst-import")
public class GstImportController {

    private final GstImportService service;

    public GstImportController(GstImportService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GstImportBatchDto>> importFile(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Only .xlsx files are supported"));
        }
        try {
            GstImportBatchDto result = service.importGstr2b(file, userName);
            return ResponseEntity.ok(ApiResponse.ok(
                    "Imported: " + result.getRowsMatched() + " of " + result.getRowsRead() + " invoice(s) matched", result));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Import failed: " + e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<GstImportBatchDto>>> history() {
        return ResponseEntity.ok(ApiResponse.ok("GST import history", service.getHistory()));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) {
        GstImportBatch batch = service.getBatchOrThrow(id);
        Path file = service.resolveStoredFile(id);
        Resource resource = new FileSystemResource(file);
        if (!resource.exists()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header("Content-Disposition", "attachment; filename=\"" + batch.getOriginalFilename() + "\"")
                .body(resource);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(404).body(ApiResponse.error(ex.getMessage()));
    }
}
