package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.model.BugComment;
import com.pwj.tracker.model.BugReport;
import com.pwj.tracker.service.BugReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/bugs")
@RequiredArgsConstructor
public class BugReportController {

    private final BugReportService service;

    @PostMapping
    public ResponseEntity<ApiResponse<BugReport>> create(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Bug reported",
                service.create(body.get("reportedBy"), body.get("title"), body.get("description"),
                        body.get("module"), body.get("severity"), body.get("attachmentUrl"))));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BugReport>>> getAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) String search) {
        boolean noFilters = status == null && severity == null && module == null
                && assignedTo == null && search == null;
        List<BugReport> bugs = noFilters ? service.getAll()
                : service.getFiltered(status, severity, module, assignedTo, search);
        return ResponseEntity.ok(ApiResponse.ok("Bugs", bugs));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BugReport>> update(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Bug updated",
                service.update(id, body.get("title"), body.get("description"),
                        body.get("module"), body.get("severity"), body.get("actorUsername"))));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<BugReport>> updateStatus(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated",
                service.updateStatus(id, body.get("status"), body.get("actorUsername"))));
    }

    @PutMapping("/{id}/severity")
    public ResponseEntity<ApiResponse<BugReport>> updateSeverity(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Severity updated",
                service.updateSeverity(id, body.get("severity"), body.get("actorUsername"))));
    }

    @PutMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<BugReport>> assign(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Assigned",
                service.assign(id, body.get("assignedTo"), body.get("actorUsername"))));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<List<BugComment>>> getComments(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Comments", service.getComments(id)));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<BugComment>> addComment(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Comment added",
                service.addComment(id, body.get("commentText"), body.get("actorUsername"))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handle(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }
}
