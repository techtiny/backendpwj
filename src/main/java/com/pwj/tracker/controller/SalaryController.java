package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.dto.SalaryDto;
import com.pwj.tracker.model.EmployeeSalary;
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
        LocalDate now = LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok("Salary sheet",
                salaryService.sheet(year != null ? year : now.getYear(), month != null ? month : now.getMonthValue())));
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
