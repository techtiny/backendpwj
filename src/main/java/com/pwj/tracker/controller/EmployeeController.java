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

    /** POST /api/v1/hr/employees/from-login/{username} — create (and link) the Employee Master record for a login that has none. */
    @PostMapping("/from-login/{username}")
    public ResponseEntity<ApiResponse<Employee>> fromLogin(@PathVariable String username) {
        return ResponseEntity.ok(ApiResponse.ok("Employee profile ready", service.createFromLogin(username)));
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
        String parameter = body.get("parameter") != null ? body.get("parameter").toString() : null;
        String category = body.get("category") != null ? body.get("category").toString() : null;
        return ResponseEntity.ok(ApiResponse.ok("Remark saved", service.addRemark(id, date, rating, notes, actionBy, actorRole, parameter, category)));
    }

    /** POST /api/v1/hr/employees/{id}/remarks/{remarkId}/appeal — body: { text, username }. */
    @PostMapping("/{id}/remarks/{remarkId}/appeal")
    public ResponseEntity<ApiResponse<PerformanceRemark>> appeal(@PathVariable Long id, @PathVariable Long remarkId, @RequestBody Map<String, Object> body) {
        String text = body.get("text") != null ? body.get("text").toString() : null;
        String username = body.get("username") != null ? body.get("username").toString() : null;
        return ResponseEntity.ok(ApiResponse.ok("Appeal submitted", service.appealRemark(id, remarkId, text, username)));
    }

    /** POST /api/v1/hr/employees/{id}/remarks/{remarkId}/acknowledge — body: { username }. Employee ticks (accepts) the comment. */
    @PostMapping("/{id}/remarks/{remarkId}/acknowledge")
    public ResponseEntity<ApiResponse<PerformanceRemark>> acknowledge(@PathVariable Long id, @PathVariable Long remarkId, @RequestBody Map<String, Object> body) {
        String username = body.get("username") != null ? body.get("username").toString() : null;
        return ResponseEntity.ok(ApiResponse.ok("Comment accepted", service.acknowledgeRemark(id, remarkId, username)));
    }

    /** PUT /api/v1/hr/employees/{id}/remarks/{remarkId}/rating — body: { rating, category, actionBy, role } (after the 48h window, no appeal). */
    @PutMapping("/{id}/remarks/{remarkId}/rating")
    public ResponseEntity<ApiResponse<PerformanceRemark>> rate(@PathVariable Long id, @PathVariable Long remarkId, @RequestBody Map<String, Object> body) {
        Integer rating = body.get("rating") != null ? Integer.valueOf(body.get("rating").toString()) : null;
        String by = body.get("actionBy") != null ? body.get("actionBy").toString() : null;
        String role = body.get("role") != null ? body.get("role").toString() : null;
        String category = body.get("category") != null ? body.get("category").toString() : null;
        return ResponseEntity.ok(ApiResponse.ok("Rating saved", service.rateRemark(id, remarkId, rating, category, by, role)));
    }

    /** POST /api/v1/hr/employees/{id}/remarks/{remarkId}/decision — body: { decision: ACCEPT|REJECT, comment, rating, category (REJECT), actionBy, role }. */
    @PostMapping("/{id}/remarks/{remarkId}/decision")
    public ResponseEntity<ApiResponse<PerformanceRemark>> decide(@PathVariable Long id, @PathVariable Long remarkId, @RequestBody Map<String, Object> body) {
        String decision = body.get("decision") != null ? body.get("decision").toString() : null;
        String comment = body.get("comment") != null ? body.get("comment").toString() : null;
        String by = body.get("actionBy") != null ? body.get("actionBy").toString() : null;
        String role = body.get("role") != null ? body.get("role").toString() : null;
        Integer rating = body.get("rating") != null ? Integer.valueOf(body.get("rating").toString()) : null;
        String category = body.get("category") != null ? body.get("category").toString() : null;
        return ResponseEntity.ok(ApiResponse.ok("Appeal reviewed", service.decideAppeal(id, remarkId, decision, comment, rating, category, by, role)));
    }

    /** GET /api/v1/hr/employees/remarks/appeals/pending — appeals waiting for a reviewer's decision. */
    @GetMapping("/remarks/appeals/pending")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> pendingAppeals() {
        return ResponseEntity.ok(ApiResponse.ok("Pending appeals", service.pendingAppeals()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleError(RuntimeException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
    }
}
