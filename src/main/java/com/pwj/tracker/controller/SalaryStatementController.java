package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.model.SalaryStatementRequest;
import com.pwj.tracker.service.SalaryStatementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/hr/salary-statement")
@RequiredArgsConstructor
public class SalaryStatementController {

    private final SalaryStatementService service;

    /** POST — body: username, year, month. */
    @PostMapping
    public ResponseEntity<ApiResponse<SalaryStatementRequest>> request(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok("Salary statement requested",
                service.request(str(body.get("username")), num(body.get("year")), num(body.get("month")))));
    }

    @GetMapping("/my/{username}")
    public ResponseEntity<ApiResponse<List<SalaryStatementRequest>>> mine(@PathVariable String username) {
        return ResponseEntity.ok(ApiResponse.ok("My statements", service.mine(username)));
    }

    /** Admin / VP — requests waiting for approval. */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<SalaryStatementRequest>>> pending(@RequestParam String username) {
        return ResponseEntity.ok(ApiResponse.ok("Pending statements", service.pending(username)));
    }

    /** Admin / VP / OH / CEO — the issue log for monitoring. */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<SalaryStatementRequest>>> all(@RequestParam String username) {
        return ResponseEntity.ok(ApiResponse.ok("All statements", service.all(username)));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<SalaryStatementRequest>> approve(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok("Approved", service.decide(id, str(body.get("username")), true, str(body.get("comment")))));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<SalaryStatementRequest>> reject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok("Rejected", service.decide(id, str(body.get("username")), false, str(body.get("comment")))));
    }

    /** Employee downloaded an approved statement — counted for the monitoring log. */
    @PostMapping("/{id}/downloaded")
    public ResponseEntity<ApiResponse<SalaryStatementRequest>> downloaded(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok("Recorded", service.recordDownload(id, str(body.get("username")))));
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
    private static Integer num(Object o) { return o == null || o.toString().isBlank() ? null : Integer.valueOf(o.toString()); }
}
