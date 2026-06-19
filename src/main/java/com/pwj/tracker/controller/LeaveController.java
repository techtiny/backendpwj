package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.model.LeaveRequest;
import com.pwj.tracker.service.LeaveRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/hr/leave")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveRequestService service;

    /** POST /api/v1/hr/leave */
    @PostMapping
    public ResponseEntity<ApiResponse<LeaveRequest>> apply(@RequestBody Map<String, String> body) {
        Integer permissionHours = body.get("permissionHours") != null
                ? Integer.parseInt(body.get("permissionHours")) : null;
        return ResponseEntity.ok(ApiResponse.ok("Leave applied",
                service.apply(
                        body.get("username"),
                        body.get("leaveType"),
                        LocalDate.parse(body.get("fromDate")),
                        body.get("toDate") != null ? LocalDate.parse(body.get("toDate")) : LocalDate.parse(body.get("fromDate")),
                        body.get("reason"),
                        body.get("attachmentUrl"),
                        permissionHours
                )));
    }

    /** PUT /api/v1/hr/leave/{id}/approve */
    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<LeaveRequest>> approve(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Leave approved",
                service.approve(id, body.get("approvedBy"), body.getOrDefault("comment", ""))));
    }

    /** PUT /api/v1/hr/leave/{id}/reject */
    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<LeaveRequest>> reject(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Leave rejected",
                service.reject(id, body.get("approvedBy"), body.getOrDefault("comment", ""))));
    }

    /** PUT /api/v1/hr/leave/{id}/cancel */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<LeaveRequest>> cancel(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Leave cancelled",
                service.cancel(id, body.get("username"))));
    }

    /** GET /api/v1/hr/leave/my/{username} */
    @GetMapping("/my/{username}")
    public ResponseEntity<ApiResponse<List<LeaveRequest>>> myLeaves(@PathVariable String username) {
        return ResponseEntity.ok(ApiResponse.ok("My leaves", service.getMyLeaves(username)));
    }

    /** GET /api/v1/hr/leave/pending */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<LeaveRequest>>> pending() {
        return ResponseEntity.ok(ApiResponse.ok("Pending approvals", service.getPending()));
    }

    /** GET /api/v1/hr/leave/all */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<LeaveRequest>>> all() {
        return ResponseEntity.ok(ApiResponse.ok("All leaves", service.getAll()));
    }

    /** GET /api/v1/hr/leave/summary/{username} */
    @GetMapping("/summary/{username}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary(@PathVariable String username) {
        return ResponseEntity.ok(ApiResponse.ok("Summary", service.getMySummary(username)));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handle(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }
}
