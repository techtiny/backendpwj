package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.dto.SalaryDto;
import com.pwj.tracker.model.EmployeeSalary;
import com.pwj.tracker.service.SalaryApprovalService;
import com.pwj.tracker.service.SalaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/hr/salary")
@RequiredArgsConstructor
public class SalaryController {

    private final SalaryService salaryService;
    private final SalaryApprovalService approvalService;

    /** GET /api/v1/hr/salary/structures — every employee + their current structure (or none). */
    @GetMapping("/structures")
    public ResponseEntity<ApiResponse<List<SalaryDto.StructureView>>> structures() {
        return ResponseEntity.ok(ApiResponse.ok("Structures fetched", salaryService.listStructures()));
    }

    /** POST /api/v1/hr/salary/structure — define a new employee's salary, or add an appraisal revision. */
    @PostMapping("/structure")
    public ResponseEntity<ApiResponse<EmployeeSalary>> saveStructure(@RequestBody SalaryDto.StructureRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Salary structure saved", salaryService.saveStructure(req)));
    }

    /** GET /api/v1/hr/salary/structure/{userId}/history — revision history (appraisals). */
    @GetMapping("/structure/{userId}/history")
    public ResponseEntity<ApiResponse<List<EmployeeSalary>>> history(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("History fetched", salaryService.structureHistory(userId)));
    }

    /** GET /api/v1/hr/salary/sheet?year=&month= — computed monthly salary sheet (defaults to current month). */
    @GetMapping("/sheet")
    public ResponseEntity<ApiResponse<List<SalaryDto.SheetRow>>> sheet(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        // Default = the payroll month in hand: the 1st–7th still belongs to last month's salary (finalised until the 7th).
        LocalDate now = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        LocalDate def = now.getDayOfMonth() <= 7 ? now.minusMonths(1) : now;
        return ResponseEntity.ok(ApiResponse.ok("Salary sheet",
                salaryService.sheet(year != null ? year : def.getYear(), month != null ? month : def.getMonthValue())));
    }

    /** POST /api/v1/hr/salary/sheet/{userId}/regularize?year=&month= — record an unapplied, unauthorized day as approved leave. */
    @PostMapping("/sheet/{userId}/regularize")
    public ResponseEntity<ApiResponse<SalaryDto.SheetRow>> regularize(
            @PathVariable Long userId, @RequestParam int year, @RequestParam int month,
            @RequestBody SalaryDto.RegularizeRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Leave recorded", salaryService.regularizeAbsence(userId, year, month, req)));
    }

    /** GET /api/v1/hr/salary/approval?year=&month= — release-approval state of one month's sheet. */
    @GetMapping("/approval")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> approval(@RequestParam int year, @RequestParam int month) {
        return ResponseEntity.ok(ApiResponse.ok("Approval", approvalService.view(year, month)));
    }

    /** POST /api/v1/hr/salary/approval/approve — body { username }. Admin → OH/VP → CEO (final = released). */
    @PostMapping("/approval/approve")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> approve(
            @RequestParam int year, @RequestParam int month, @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Approved", approvalService.approve(year, month, body.get("username"))));
    }

    /** POST /api/v1/hr/salary/approval/reject — body { username, remark }. Sends the chain back to Admin. */
    @PostMapping("/approval/reject")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> reject(
            @RequestParam int year, @RequestParam int month, @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Rejected", approvalService.reject(year, month, body.get("username"), body.get("remark"))));
    }

    /** POST /api/v1/hr/salary/approval/revise — VP only, even after the CEO released the month. body { username, remark }. */
    @PostMapping("/approval/revise")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> revise(
            @RequestParam int year, @RequestParam int month, @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Sent back to Admin", approvalService.revise(year, month, body.get("username"), body.get("remark"))));
    }

    /** PATCH /api/v1/hr/salary/sheet/{userId}?year=&month= — HR override for one employee's month. */
    @PatchMapping("/sheet/{userId}")
    public ResponseEntity<ApiResponse<SalaryDto.SheetRow>> adjust(
            @PathVariable Long userId,
            @RequestParam int year, @RequestParam int month,
            @RequestBody SalaryDto.AdjustmentRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Adjustment saved", salaryService.saveAdjustment(userId, year, month, req)));
    }
}
