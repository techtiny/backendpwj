package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.service.SalaryPayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Salary payments for released months — shown in Accounts → Send for Payment. */
@RestController
@RequestMapping("/api/v1/hr/salary/payouts")
@RequiredArgsConstructor
public class SalaryPayoutController {

    private final SalaryPayoutService service;

    /** GET /api/v1/hr/salary/payouts?username= — Admin / VP / OH / CEO: every released month's payment batch. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(@RequestParam String username) {
        return ResponseEntity.ok(ApiResponse.ok("Salary payments", service.list(username)));
    }

    /** POST /api/v1/hr/salary/payouts/{year}/{month}/approve — VP only. body: { username } */
    @PostMapping("/{year}/{month}/approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approve(@PathVariable int year, @PathVariable int month, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Approved", service.approve(year, month, body.get("username"))));
    }

    /** POST /api/v1/hr/salary/payouts/{year}/{month}/reject — VP only. body: { username, remark } */
    @PostMapping("/{year}/{month}/reject")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reject(@PathVariable int year, @PathVariable int month, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Rejected", service.reject(year, month, body.get("username"), body.get("remark"))));
    }
}
