package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.model.SalaryAdvance;
import com.pwj.tracker.service.SalaryAdvanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/hr/advance")
@RequiredArgsConstructor
public class SalaryAdvanceController {

    private final SalaryAdvanceService service;

    /** POST /api/v1/hr/advance — body: username, amount, reason, termMonths. */
    @PostMapping
    public ResponseEntity<ApiResponse<SalaryAdvance>> raise(@RequestBody Map<String, Object> body) {
        BigDecimal amount = body.get("amount") != null && !body.get("amount").toString().isBlank() ? new BigDecimal(body.get("amount").toString()) : null;
        Integer term = body.get("termMonths") != null && !body.get("termMonths").toString().isBlank() ? Integer.valueOf(body.get("termMonths").toString()) : null;
        return ResponseEntity.ok(ApiResponse.ok("Advance requested",
                service.raise(str(body.get("username")), amount, str(body.get("reason")), term)));
    }

    @GetMapping("/my/{username}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> mine(@PathVariable String username) {
        return ResponseEntity.ok(ApiResponse.ok("My advances", service.mine(username)));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> pending() {
        return ResponseEntity.ok(ApiResponse.ok("Pending advances", service.pending()));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> all() {
        return ResponseEntity.ok(ApiResponse.ok("All advances", service.all()));
    }

    /** PUT /api/v1/hr/advance/{id}/approve — OH / VP. body: username, comment, termMonths (optional change). */
    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<SalaryAdvance>> approve(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer term = body.get("termMonths") != null && !body.get("termMonths").toString().isBlank() ? Integer.valueOf(body.get("termMonths").toString()) : null;
        return ResponseEntity.ok(ApiResponse.ok("Advance approved", service.approve(id, str(body.get("username")), str(body.get("comment")), term)));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<SalaryAdvance>> reject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok("Advance rejected", service.reject(id, str(body.get("username")), str(body.get("comment")))));
    }

    /** PUT /api/v1/hr/advance/{id}/paid — Admin: money transferred, repayment starts. */
    @PutMapping("/{id}/paid")
    public ResponseEntity<ApiResponse<SalaryAdvance>> paid(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok("Marked as paid", service.markPaid(id, str(body.get("username")))));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<SalaryAdvance>> cancel(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok("Advance cancelled", service.cancel(id, str(body.get("username")))));
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
}
