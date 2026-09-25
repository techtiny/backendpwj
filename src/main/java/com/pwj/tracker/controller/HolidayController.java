package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.model.Holiday;
import com.pwj.tracker.service.HolidayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/hr/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final HolidayService service;

    /** GET /api/v1/hr/holidays — every declared holiday, earliest first. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Holiday>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("Holidays fetched", service.list()));
    }

    /** POST /api/v1/hr/holidays — body: { date: "2026-01-01", occasion: "New Year", actionBy } */
    @PostMapping
    public ResponseEntity<ApiResponse<Holiday>> add(@RequestBody Map<String, Object> body) {
        LocalDate date = LocalDate.parse(body.get("date").toString());
        String occasion = (String) body.get("occasion");
        String actionBy = (String) body.get("actionBy");
        return ResponseEntity.ok(ApiResponse.ok("Holiday added", service.add(date, occasion, actionBy)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Holiday removed", null));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleError(RuntimeException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
    }
}
