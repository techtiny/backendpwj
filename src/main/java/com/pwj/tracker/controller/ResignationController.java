package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.model.Resignation;
import com.pwj.tracker.service.ResignationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/hr/resignation")
@RequiredArgsConstructor
public class ResignationController {

    private final ResignationService service;

    /** The signed-in employee's current resignation (null when none). */
    @GetMapping("/my/{username}")
    public ResponseEntity<ApiResponse<Resignation>> mine(@PathVariable String username) {
        return ResponseEntity.ok(ApiResponse.ok("My resignation", service.mine(username)));
    }

    /** VP / Admin / OH / CEO — every resignation. */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<Resignation>>> all(@RequestParam String username) {
        return ResponseEntity.ok(ApiResponse.ok("Resignations", service.all(username)));
    }

    /** VP / Admin enables resignation for an employee — body: username (actor), employee. */
    @PostMapping("/enable")
    public ResponseEntity<ApiResponse<Resignation>> enable(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok("Resignation enabled", service.enable(str(body.get("username")), str(body.get("employee")))));
    }

    /** Employee applies — body: username, reason. */
    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<Resignation>> apply(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok("Resignation submitted", service.apply(str(body.get("username")), str(body.get("reason")))));
    }

    /** VP / Admin changes it — body: username (actor), resignationDate?, lastWorkingDay?, reason?, remarks?, status?. */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Resignation>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok("Resignation updated", service.update(id, str(body.get("username")),
                date(body.get("resignationDate")), date(body.get("lastWorkingDay")),
                str(body.get("reason")), str(body.get("remarks")), str(body.get("status")))));
    }

    @PutMapping("/{id}/disable")
    public ResponseEntity<ApiResponse<Resignation>> disable(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok("Resignation disabled", service.disable(id, str(body.get("username")))));
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
    private static LocalDate date(Object o) { return o == null || o.toString().isBlank() ? null : LocalDate.parse(o.toString()); }
}
