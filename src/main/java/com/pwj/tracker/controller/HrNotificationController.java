package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.service.HrNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/hr/notifications")
@RequiredArgsConstructor
public class HrNotificationController {

    private final HrNotificationService service;

    /** GET /api/v1/hr/notifications?username= — HR items waiting on this user (shown on the Home page). */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(@RequestParam String username) {
        return ResponseEntity.ok(ApiResponse.ok("Notifications", service.forUser(username)));
    }
}
