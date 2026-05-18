package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.service.BackupService;
import com.pwj.tracker.service.ExcelExportService;
import com.pwj.tracker.service.WeeklyReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/report")
@RequiredArgsConstructor
public class ReportController {

    private final WeeklyReportService weeklyReportService;
    private final ExcelExportService excelExportService;
    private final BackupService backupService;

    /**
     * POST /api/v1/report/send-now
     * Manually trigger the weekly email report (for testing)
     */
    @PostMapping("/send-now")
    public ResponseEntity<ApiResponse<String>> sendNow() {
        weeklyReportService.sendWeeklyReport();
        return ResponseEntity.ok(ApiResponse.ok("Report email triggered", "Check inbox at configured recipient"));
    }

    /**
     * POST /api/v1/report/trigger-backup
     * Manually trigger the full system backup (Excel + DB dump) email
     */
    @PostMapping("/trigger-backup")
    public ResponseEntity<ApiResponse<String>> triggerBackup() {
        try {
            backupService.triggerBackup();
            return ResponseEntity.ok(ApiResponse.ok("Backup sent", "Excel + DB dump emailed to " + backupService.getBackupTo()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error("Backup failed: " + e.getMessage()));
        }
    }

    /**
     * GET /api/v1/report/download
     * Download the Excel report directly in the browser (for testing)
     */
    @GetMapping("/download")
    public ResponseEntity<byte[]> download() {
        byte[] bytes = excelExportService.generateWeeklyReport();
        String filename = "PWJ-Report-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(bytes);
    }
}
