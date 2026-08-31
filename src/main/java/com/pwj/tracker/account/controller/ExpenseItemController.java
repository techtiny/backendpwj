package com.pwj.tracker.account.controller;

import com.pwj.tracker.account.dto.ExpenseItemDto;
import com.pwj.tracker.account.dto.SendForPaymentRequest;
import com.pwj.tracker.account.service.ExpenseItemService;
import com.pwj.tracker.repository.ProjectRepository;
import com.pwj.tracker.service.PwjEntryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.PatchMapping;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseItemController {

    private final ExpenseItemService service;
    private final PwjEntryService pwjEntryService;
    private final ProjectRepository projectRepo;

    public ExpenseItemController(ExpenseItemService service, PwjEntryService pwjEntryService,
                                  ProjectRepository projectRepo) {
        this.service         = service;
        this.pwjEntryService = pwjEntryService;
        this.projectRepo     = projectRepo;
    }

    @GetMapping("/{projectId}/{category}")
    public ResponseEntity<List<ExpenseItemDto>> getItems(
            @PathVariable Long projectId, @PathVariable String category) {
        return ResponseEntity.ok(service.getByProjectAndCategory(projectId, category));
    }

    @GetMapping("/{projectId}/{category}/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @PathVariable Long projectId, @PathVariable String category) {
        return ResponseEntity.ok(service.getSummary(projectId, category));
    }

    @PostMapping
    public ResponseEntity<ExpenseItemDto> create(@RequestBody ExpenseItemDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExpenseItemDto> update(@PathVariable Long id, @RequestBody ExpenseItemDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/pwj-docs")
    public ResponseEntity<List<Map<String, Object>>> getPwjDocs(
            @RequestParam(required = false) Long projectId) {
        List<Map<String, Object>> all = pwjEntryService.getDocSummaries();
        if (projectId == null) return ResponseEntity.ok(all);

        String projectName = projectRepo.findById(projectId)
                .map(p -> p.getName())
                .orElse(null);
        if (projectName == null) return ResponseEntity.ok(List.of());

        final String name = projectName;
        List<Map<String, Object>> filtered = all.stream()
                .filter(d -> name.equalsIgnoreCase((String) d.get("projectName")))
                .collect(Collectors.toList());
        return ResponseEntity.ok(filtered);
    }

    @GetMapping("/{projectId}/tracked-refs")
    public ResponseEntity<List<String>> getTrackedRefs(@PathVariable Long projectId) {
        return ResponseEntity.ok(service.getTrackedRefs(projectId));
    }

    @PatchMapping("/{id}/move")
    public ResponseEntity<ExpenseItemDto> moveCategory(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(service.moveCategory(id, body.get("category")));
    }

    /** PATCH /api/expenses/{id}/eligibility — body: { "eligible": true|false } */
    @PatchMapping("/{id}/eligibility")
    public ResponseEntity<ExpenseItemDto> setEligibility(
            @PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return ResponseEntity.ok(service.setEligibility(id, Boolean.TRUE.equals(body.get("eligible"))));
    }

    /**
     * POST /api/expenses/send-for-payment
     * Body: { "items": [{ "id": 1, "paymentType": "FULL" }, { "id": 2, "paymentType": "PART", "amount": 5000 }] }
     * Sends the selected, already-eligible entries for payment. Atomic across the batch —
     * validation includes the per-PO cap (total sent per refNo can't exceed its derived value).
     */
    @PostMapping("/send-for-payment")
    public ResponseEntity<List<ExpenseItemDto>> sendForPayment(@RequestBody SendForPaymentRequest req) {
        return ResponseEntity.ok(service.sendForPayment(req.getItems()));
    }

    /** GET /api/expenses/sent-for-payment — cross-project tracker of everything sent for payment */
    @GetMapping("/sent-for-payment")
    public ResponseEntity<List<ExpenseItemDto>> getSentForPayment() {
        return ResponseEntity.ok(service.getSentForPayment());
    }

    /**
     * POST /api/expenses/send-pwj-for-payment
     * Body: { "pwjEntryId": 123, "amount": 50000 }
     * Procurement: send a payment request straight from an issued PWJ entry — vendor,
     * doc number and project are auto-derived from the entry.
     */
    @PostMapping("/send-pwj-for-payment")
    public ResponseEntity<ExpenseItemDto> sendPwjEntryForPayment(@RequestBody Map<String, Object> body) {
        Long pwjEntryId = Long.valueOf(String.valueOf(body.get("pwjEntryId")));
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        String remarks = body.get("remarks") != null ? String.valueOf(body.get("remarks")) : null;
        String paymentMadeAgainst = body.get("paymentMadeAgainst") != null ? String.valueOf(body.get("paymentMadeAgainst")) : null;
        return ResponseEntity.ok(service.sendPwjEntryForPayment(pwjEntryId, amount, remarks, paymentMadeAgainst));
    }

    /**
     * GET /api/expenses/pwj-entry/{id}/payment-availability
     * Procurement: how much of this issued PWJ doc's value is still available to send for
     * payment — { docNumber, vendor, poValue, alreadySent, available, resolved }.
     */
    @GetMapping("/pwj-entry/{id}/payment-availability")
    public ResponseEntity<Map<String, Object>> getPwjEntryPaymentAvailability(@PathVariable Long id) {
        return ResponseEntity.ok(service.getPwjEntryPaymentAvailability(id));
    }

    /**
     * PATCH /api/expenses/{id}/oh-approval
     * Body: { "status": "APPROVED"|"REJECTED", "revisedAmount": 12345.00 }
     * OH may revise the sent amount before approving/rejecting.
     */
    @PatchMapping("/{id}/oh-approval")
    public ResponseEntity<ExpenseItemDto> setOhApproval(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        String status = (String) body.get("status");
        BigDecimal revisedAmount = body.get("revisedAmount") != null
                ? new BigDecimal(body.get("revisedAmount").toString()) : null;
        return ResponseEntity.ok(service.setOhApproval(id, status, revisedAmount));
    }

    /** PATCH /api/expenses/{id}/admin-approval — Body: { "status": "APPROVED"|"REJECTED" }. Requires OH approved. */
    @PatchMapping("/{id}/admin-approval")
    public ResponseEntity<ExpenseItemDto> setAdminApproval(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(service.setAdminApproval(id, body.get("status")));
    }

    /** PATCH /api/expenses/{id}/vp-approval — Body: { "status": "APPROVED"|"REJECTED" }. Requires Admin approved. */
    @PatchMapping("/{id}/vp-approval")
    public ResponseEntity<ExpenseItemDto> setVpApproval(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(service.setVpApproval(id, body.get("status")));
    }

    /**
     * PATCH /api/expenses/{id}/deductions
     * Body: { "tdsPercent": 1|2|10|null, "gstDeducted": true|false }
     * Sets the Send-for-Payment deductions; TDS Amt / GST Amt / Approved Value are recomputed.
     */
    @PatchMapping("/{id}/deductions")
    public ResponseEntity<ExpenseItemDto> setDeductions(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal tdsPercent = body.get("tdsPercent") != null && !String.valueOf(body.get("tdsPercent")).isBlank()
                ? new BigDecimal(String.valueOf(body.get("tdsPercent"))) : null;
        Boolean gstDeducted = body.get("gstDeducted") != null && Boolean.parseBoolean(String.valueOf(body.get("gstDeducted")));
        return ResponseEntity.ok(service.setDeductions(id, tdsPercent, gstDeducted));
    }

    @PostMapping("/repair-categories")
    public ResponseEntity<Map<String, Object>> repairCategories() {
        return ResponseEntity.ok(Map.of("fixed", service.repairCategories()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
}
