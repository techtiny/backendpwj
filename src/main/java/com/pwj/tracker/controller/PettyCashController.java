package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.model.PettyCash;
import com.pwj.tracker.service.PettyCashService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/hr/petty-cash")
@RequiredArgsConstructor
public class PettyCashController {

    private final PettyCashService service;

    @PostMapping
    public ResponseEntity<ApiResponse<PettyCash>> create(@RequestBody Map<String, Object> body) {
        String username    = (String) body.get("username");
        String category    = (String) body.get("category");
        String description = (String) body.get("description");
        String paymentMode = (String) body.getOrDefault("paymentMode", "CASH");
        BigDecimal amount  = new BigDecimal(body.get("amount").toString());
        LocalDate date     = body.get("expenseDate") != null
                ? LocalDate.parse(body.get("expenseDate").toString()) : null;
        String attachmentUrl = body.get("attachmentUrl") != null
                ? body.get("attachmentUrl").toString() : null;
        String projectName = body.get("projectName") != null
                ? body.get("projectName").toString() : null;
        return ResponseEntity.ok(ApiResponse.ok("Entry created",
                service.create(username, date, category, description, amount, paymentMode, attachmentUrl, projectName)));
    }

    @GetMapping("/my/{username}")
    public ResponseEntity<ApiResponse<List<PettyCash>>> getMyEntries(@PathVariable String username) {
        return ResponseEntity.ok(ApiResponse.ok("My entries", service.getMyEntries(username)));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<PettyCash>>> getPending() {
        return ResponseEntity.ok(ApiResponse.ok("Pending entries", service.getPending()));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<PettyCash>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("All entries", service.getAll()));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<PettyCash>> approve(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Approved",
                service.approve(id, body.get("approvedBy"), body.get("approvedByRole"), body.getOrDefault("comment", ""))));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<PettyCash>> reject(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Rejected",
                service.reject(id, body.get("approvedBy"), body.get("approvedByRole"), body.getOrDefault("comment", ""))));
    }

    @PutMapping("/{id}/mark-transferred")
    public ResponseEntity<ApiResponse<PettyCash>> markTransferred(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Cash marked as transferred", service.markTransferred(id)));
    }

    @PutMapping("/{id}/submit-proof")
    public ResponseEntity<ApiResponse<PettyCash>> submitProof(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        @SuppressWarnings("unchecked")
        java.util.List<String> proofUrls = (java.util.List<String>) body.get("proofUrls");
        return ResponseEntity.ok(ApiResponse.ok("Proof submitted",
                service.submitProof(id, username, proofUrls)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id, @RequestParam String username) {
        service.delete(id, username);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }

    @GetMapping("/summary/{username}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(@PathVariable String username) {
        return ResponseEntity.ok(ApiResponse.ok("Summary", service.getSummary(username)));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handle(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }
}
