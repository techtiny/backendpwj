package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.model.Attendance;
import com.pwj.tracker.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/hr/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService service;

    /** POST /api/v1/hr/attendance/checkin */
    @PostMapping("/checkin")
    public ResponseEntity<ApiResponse<Attendance>> checkIn(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        Double lat  = body.get("lat")  != null ? Double.parseDouble(body.get("lat").toString())  : null;
        Double lng  = body.get("lng")  != null ? Double.parseDouble(body.get("lng").toString())  : null;
        String addr = (String) body.getOrDefault("address", "");
        return ResponseEntity.ok(ApiResponse.ok("Checked in successfully", service.checkIn(username, lat, lng, addr)));
    }

    /** POST /api/v1/hr/attendance/checkout */
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<Attendance>> checkOut(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        Double lat  = body.get("lat")  != null ? Double.parseDouble(body.get("lat").toString())  : null;
        Double lng  = body.get("lng")  != null ? Double.parseDouble(body.get("lng").toString())  : null;
        String addr = (String) body.getOrDefault("address", "");
        return ResponseEntity.ok(ApiResponse.ok("Checked out successfully", service.checkOut(username, lat, lng, addr)));
    }

    /** GET /api/v1/hr/attendance/today/{username} */
    @GetMapping("/today/{username}")
    public ResponseEntity<ApiResponse<Attendance>> getToday(@PathVariable String username) {
        return ResponseEntity.ok(ApiResponse.ok("Today record",
                service.getTodayRecord(username).orElse(null)));
    }

    /** GET /api/v1/hr/attendance/history/{username} */
    @GetMapping("/history/{username}")
    public ResponseEntity<ApiResponse<List<Attendance>>> getHistory(@PathVariable String username) {
        return ResponseEntity.ok(ApiResponse.ok("History", service.getUserHistory(username)));
    }

    /** GET /api/v1/hr/attendance/today-all  (admin/VP) */
    @GetMapping("/today-all")
    public ResponseEntity<ApiResponse<List<Attendance>>> getTodayAll() {
        return ResponseEntity.ok(ApiResponse.ok("Today all", service.getTodayAll()));
    }

    /** GET /api/v1/hr/attendance/all  (admin) */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<Attendance>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("All records", service.getAll()));
    }

    /** GET /api/v1/hr/attendance/summary/{username} */
    @GetMapping("/summary/{username}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(@PathVariable String username) {
        return ResponseEntity.ok(ApiResponse.ok("Summary", service.getSummary(username)));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handle(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }
}
