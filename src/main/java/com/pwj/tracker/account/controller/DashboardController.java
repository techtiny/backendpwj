package com.pwj.tracker.account.controller;

import com.pwj.tracker.account.dto.DashboardStatsDto;
import com.pwj.tracker.account.repository.ExpenseItemRepository;
import com.pwj.tracker.account.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final ExpenseItemRepository expenseItemRepository;

    public DashboardController(DashboardService dashboardService, ExpenseItemRepository expenseItemRepository) {
        this.dashboardService = dashboardService;
        this.expenseItemRepository = expenseItemRepository;
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDto> getStats() {
        return ResponseEntity.ok(dashboardService.getStats());
    }

    @GetMapping("/project-expenses")
    public ResponseEntity<List<Map<String, Object>>> getProjectExpenses() {
        return ResponseEntity.ok(dashboardService.getStats().getProjectExpenses());
    }

    @GetMapping("/vendor-gst-by-project")
    public ResponseEntity<Map<Long, BigDecimal>> getVendorGstByProject() {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        expenseItemRepository.getTotalVendorGstByProject().forEach(r ->
            result.put(((Number) r[0]).longValue(), (BigDecimal) r[1])
        );
        return ResponseEntity.ok(result);
    }
}
