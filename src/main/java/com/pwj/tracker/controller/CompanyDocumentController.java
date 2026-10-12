package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.model.CompanyDocument;
import com.pwj.tracker.service.CompanyDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/company-docs")
@RequiredArgsConstructor
public class CompanyDocumentController {

    private final CompanyDocumentService service;

    /** GET /api/v1/company-docs — every company document (policies, SOPs, etc.), newest first. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CompanyDocument>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("Documents fetched", service.list()));
    }

    /**
     * POST /api/v1/company-docs — register a document already uploaded via
     * /api/v1/upload/document. Body: { title, category, fileUrl, fileName, uploadedBy }
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CompanyDocument>> add(@RequestBody Map<String, String> body) {
        CompanyDocument doc = service.add(
                body.get("title"), body.get("category"), body.get("fileUrl"),
                body.get("fileName"), body.get("uploadedBy"));
        return ResponseEntity.ok(ApiResponse.ok("Document added", doc));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Document removed", null));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleError(RuntimeException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
    }
}
