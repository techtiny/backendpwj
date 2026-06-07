package com.pwj.tracker.controller;

import com.pwj.tracker.config.SseBroadcaster;
import com.pwj.tracker.dto.*;
import com.pwj.tracker.service.PwjEntryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/pwj")
@RequiredArgsConstructor
public class PwjEntryController {

    private final PwjEntryService service;
    private final SseBroadcaster  sseBroadcaster;

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no"); // disable nginx buffering on Railway
        response.setHeader("Connection", "keep-alive");
        return ResponseEntity.ok(sseBroadcaster.subscribe());
    }

    @GetMapping("/entries")
    public ResponseEntity<ApiResponse<PagedResponse<PwjEntryResponse>>> getEntries(
            @RequestParam(required = false)     String search,
            @RequestParam(required = false)     String status,
            @RequestParam(required = false)     String approval,
            @RequestParam(required = false)     String projectName,
            @RequestParam(required = false)     String raisedBy,
            @RequestParam(required = false)     String dateFrom,
            @RequestParam(required = false)     String dateTo,
            @RequestParam(defaultValue = "0")   int    page,
            @RequestParam(defaultValue = "15")  int    size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc")      String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok("Entries fetched",
                service.getAll(search, status, approval, projectName, raisedBy,
                        dateFrom, dateTo, page, size, sortBy, sortDir)));
    }

    @GetMapping("/entries/my")
    public ResponseEntity<ApiResponse<PagedResponse<PwjEntryResponse>>> getMyEntries(
            @RequestParam String raisedBy,
            @RequestParam(required = false)              String search,
            @RequestParam(required = false)              String status,
            @RequestParam(required = false)              String approval,
            @RequestParam(required = false)              String projectName,
            @RequestParam(required = false)              String dateFrom,
            @RequestParam(required = false)              String dateTo,
            @RequestParam(defaultValue = "0")            int    page,
            @RequestParam(defaultValue = "15")           int    size,
            @RequestParam(defaultValue = "updatedAt")    String sortBy,
            @RequestParam(defaultValue = "desc")         String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok("My entries fetched",
                service.getByEngineer(raisedBy, search, status, approval,
                        projectName, dateFrom, dateTo, page, size, sortBy, sortDir)));
    }

    @GetMapping("/entries/{id}")
    public ResponseEntity<ApiResponse<PwjEntryResponse>> getEntry(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Entry fetched", service.getById(id)));
    }

    @PostMapping("/entries/{id}/split-by-vendor")
    public ResponseEntity<ApiResponse<List<PwjEntryResponse>>> splitByVendor(
            @PathVariable Long id,
            @RequestBody List<com.pwj.tracker.dto.SplitVendorRequest> splits) {
        return ResponseEntity.ok(ApiResponse.ok("Split POs created", service.splitByVendor(id, splits)));
    }

    @PostMapping("/entries")
    public ResponseEntity<ApiResponse<PwjEntryResponse>> createEntry(
            @Valid @RequestBody PwjEntryRequest req,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        if (userName != null && !userName.isBlank()) req.setRaisedBy(userName);
        if (req.getRaisedBy() == null || req.getRaisedBy().isBlank())
            throw new RuntimeException("Raised by is required");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Entry created", service.create(req)));
    }

    @PutMapping("/entries/{id}")
    public ResponseEntity<ApiResponse<PwjEntryResponse>> updateEntry(
            @PathVariable Long id, @RequestBody PwjEntryRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Entry updated", service.update(id, req)));
    }

    @PatchMapping("/entries/{id}/procurement")
    public ResponseEntity<ApiResponse<PwjEntryResponse>> procurementUpdate(
            @PathVariable Long id, @RequestBody ProcurementUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Procurement updated", service.procurementUpdate(id, req)));
    }

    @PatchMapping("/entries/{id}/approval")
    public ResponseEntity<ApiResponse<PwjEntryResponse>> updateApproval(
            @PathVariable Long id, @Valid @RequestBody ApprovalRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Approval updated", service.updateApproval(id, req)));
    }

    @PatchMapping("/entries/{id}/delivery")
    public ResponseEntity<ApiResponse<PwjEntryResponse>> deliveryUpdate(
            @PathVariable Long id, @RequestBody DeliveryUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Delivery updated", service.deliveryUpdate(id, req)));
    }

    @DeleteMapping("/entries/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEntry(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Entry deleted", null));
    }

    @GetMapping("/projects")
    public ResponseEntity<ApiResponse<List<String>>> getProjects() {
        return ResponseEntity.ok(ApiResponse.ok("Projects fetched", service.getProjectNames()));
    }

    /** GET /api/v1/pwj/entries/docs — Account module: list all docs with computed totals for expense picker */
    @GetMapping("/entries/docs")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getDocSummaries() {
        return ResponseEntity.ok(ApiResponse.ok("Doc summaries", service.getDocSummaries()));
    }

    /** GET /api/v1/pwj/budget-summary — Aggregated expense totals per project: PO→material, WO→subcontract, JO→labour */
    @GetMapping("/budget-summary")
    public ResponseEntity<ApiResponse<Map<String, Map<String, Double>>>> getBudgetSummary() {
        return ResponseEntity.ok(ApiResponse.ok("Budget summary", service.getBudgetSummary()));
    }

    @GetMapping("/pending-approvals")
    public ResponseEntity<ApiResponse<List<PwjEntryResponse>>> getPendingApprovals() {
        return ResponseEntity.ok(ApiResponse.ok("Pending approvals", service.getPendingApprovals()));
    }

    /** PATCH /api/v1/pwj/entries/{id}/submit-doc — Admin/Procurement: submit doc for VP approval */
    @PatchMapping("/entries/{id}/submit-doc")
    public ResponseEntity<ApiResponse<PwjEntryResponse>> submitDoc(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Document submitted for VP approval", service.submitDoc(id)));
    }

    /** PATCH /api/v1/pwj/entries/{id}/doc-approve — VP only: approve document with optional comment */
    @PatchMapping("/entries/{id}/doc-approve")
    public ResponseEntity<ApiResponse<PwjEntryResponse>> approveDoc(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String comment = body != null ? body.get("comment") : null;
        return ResponseEntity.ok(ApiResponse.ok("Document approved", service.approveDoc(id, comment)));
    }

    /** PATCH /api/v1/pwj/entries/{id}/doc-reject — VP only: reject document with optional comment */
    @PatchMapping("/entries/{id}/doc-reject")
    public ResponseEntity<ApiResponse<PwjEntryResponse>> rejectDoc(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String comment = body != null ? body.get("comment") : null;
        return ResponseEntity.ok(ApiResponse.ok("Document rejected", service.rejectDoc(id, comment)));
    }

    /** GET /api/v1/pwj/pending-doc-approvals — VP: list all docs awaiting approval */
    @GetMapping("/pending-doc-approvals")
    public ResponseEntity<ApiResponse<List<PwjEntryResponse>>> getPendingDocApprovals() {
        return ResponseEntity.ok(ApiResponse.ok("Pending document approvals", service.getPendingDocApprovals()));
    }

    /** PATCH /api/v1/pwj/entries/{id}/toggle-vendor-email — VP/Admin: toggle vendor email on/off */
    @PatchMapping("/entries/{id}/toggle-vendor-email")
    public ResponseEntity<ApiResponse<PwjEntryResponse>> toggleVendorEmail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Vendor email toggled", service.toggleVendorEmail(id)));
    }

    /** POST /api/v1/pwj/entries/{id}/send-vendor-doc — Procurement: generate PDF from HTML and email to vendor */
    @PostMapping("/entries/{id}/send-vendor-doc")
    public ResponseEntity<ApiResponse<Void>> sendVendorDoc(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            service.sendVendorDoc(id, body.get("htmlContent"));
            return ResponseEntity.ok(ApiResponse.ok("Document sent to vendor", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
