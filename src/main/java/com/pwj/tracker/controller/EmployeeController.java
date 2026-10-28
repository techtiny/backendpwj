package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.model.Employee;
import com.pwj.tracker.model.PerformanceRemark;
import com.pwj.tracker.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/hr/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService service;

    /** GET /api/v1/hr/employees — full master roster, active + historical, name-sorted. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Employee>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("Employees fetched", service.list()));
    }

    /** POST /api/v1/hr/employees — onboard a new employee. Body: fullName, doj (required); dob, gender,
     *  department, designation, personCode (auto when blank), linkedUsername (optional). */
    @PostMapping
    public ResponseEntity<ApiResponse<Employee>> create(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok("Employee added", service.create(body)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Employee>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Employee fetched", service.get(id)));
    }

    /** PATCH /api/v1/hr/employees/{id}/photo — body: { photoUrl } (upload the file first via
     *  the generic /api/v1/upload endpoint, then set its URL here). */
    @PatchMapping("/{id}/photo")
    public ResponseEntity<ApiResponse<Employee>> updatePhoto(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String photoUrl = body.get("photoUrl") != null ? body.get("photoUrl").toString() : null;
        return ResponseEntity.ok(ApiResponse.ok("Photo updated", service.updatePhoto(id, photoUrl)));
    }

    /** PATCH /api/v1/hr/employees/{id}/link — body: { username } (or null/blank to unlink).
     *  Manual override for when the master sheet's name didn't auto-match an app login. */
    @PatchMapping("/{id}/link")
    public ResponseEntity<ApiResponse<Employee>> link(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String username = body.get("username") != null ? body.get("username").toString() : null;
        return ResponseEntity.ok(ApiResponse.ok("Link updated", service.link(id, username)));
    }

    /** GET /api/v1/hr/employees/{id}/stats?range=cycle|30d|all — PR raised + check-in accuracy. */
    @GetMapping("/{id}/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats(
            @PathVariable Long id, @RequestParam(defaultValue = "cycle") String range) {
        return ResponseEntity.ok(ApiResponse.ok("Stats fetched", service.stats(id, range)));
    }

    /** GET /api/v1/hr/employees/remarks/by-date?date=YYYY-MM-DD — all remarks of a day (default today), newest first. */
    @GetMapping("/remarks/by-date")
    public ResponseEntity<ApiResponse<List<PerformanceRemark>>> remarksByDate(@RequestParam(required = false) String date) {
        LocalDate d = date != null && !date.isBlank() ? LocalDate.parse(date) : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok("Remarks fetched", service.remarksOn(d)));
    }

    /** GET /api/v1/hr/employees/{id}/remarks — full daily performance history, newest first. */
    @GetMapping("/{id}/remarks")
    public ResponseEntity<ApiResponse<List<PerformanceRemark>>> remarks(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Remarks fetched", service.remarkHistory(id)));
    }

    /** POST /api/v1/hr/employees/{id}/remarks — body: { date, rating (1-5), notes, actionBy, actorRole }.
     *  Appends a new remark; multiple per employee per day are allowed. */
    @PostMapping("/{id}/remarks")
    public ResponseEntity<ApiResponse<PerformanceRemark>> addRemark(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        LocalDate date = body.get("date") != null ? LocalDate.parse(body.get("date").toString()) : LocalDate.now();
        Integer rating = body.get("rating") != null ? Integer.valueOf(body.get("rating").toString()) : null;
        String notes = body.get("notes") != null ? body.get("notes").toString() : null;
        String actionBy = body.get("actionBy") != null ? body.get("actionBy").toString() : null;
        String actorRole = body.get("actorRole") != null ? body.get("actorRole").toString() : null;
        return ResponseEntity.ok(ApiResponse.ok("Remark saved", service.addRemark(id, date, rating, notes, actionBy, actorRole)));
    }

    /** POST /api/v1/hr/employees/{id}/remarks/{remarkId}/appeal — body: { text, username }. */
    @PostMapping("/{id}/remarks/{remarkId}/appeal")
    public ResponseEntity<ApiResponse<PerformanceRemark>> appeal(@PathVariable Long id, @PathVariable Long remarkId, @RequestBody Map<String, Object> body) {
        String text = body.get("text") != null ? body.get("text").toString() : null;
        String username = body.get("username") != null ? body.get("username").toString() : null;
        return ResponseEntity.ok(ApiResponse.ok("Appeal submitted", service.appealRemark(id, remarkId, text, username)));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleError(RuntimeException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
    }
}
