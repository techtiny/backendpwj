package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.service.BackupService;
import com.pwj.tracker.service.ExcelExportService;
import com.pwj.tracker.service.WeeklyReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/report")
@RequiredArgsConstructor
public class ReportController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final WeeklyReportService weeklyReportService;
    private final ExcelExportService  excelExportService;
    private final BackupService       backupService;

    /** POST /send-now — trigger weekly email report */
    @PostMapping("/send-now")
    public ResponseEntity<ApiResponse<String>> sendNow() {
        weeklyReportService.sendWeeklyReport();
        return ResponseEntity.ok(ApiResponse.ok("Report email triggered", "Check inbox at configured recipient"));
    }

    /** POST /trigger-backup — send full backup ZIP via email */
    @PostMapping("/trigger-backup")
    public ResponseEntity<ApiResponse<String>> triggerBackup() {
        try {
            backupService.triggerBackup();
            return ResponseEntity.ok(ApiResponse.ok("Backup sent",
                "Full backup ZIP (DB + uploads + Excel) emailed to " + backupService.getBackupTo()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error("Backup failed: " + e.getMessage()));
        }
    }

    /** GET /download-backup — stream full backup ZIP directly (low memory, avoids gateway timeouts) */
    @GetMapping("/download-backup")
    public ResponseEntity<StreamingResponseBody> downloadBackup() {
        String filename = "PWJ-FullBackup-" + LocalDate.now(IST) + ".zip";
        StreamingResponseBody body = out -> {
            try {
                backupService.writeFullBackupZip(out);
            } catch (Exception e) {
                log.error("Backup download failed", e);
                throw new IOException("Backup generation failed: " + e.getMessage(), e);
            }
        };
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("application/zip"))
            .body(body);
    }

    /** GET /download — download Excel report only */
    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadExcel() {
        byte[] bytes = excelExportService.generateWeeklyReport();
        String filename = "PWJ-Report-" + LocalDate.now(IST) + ".xlsx";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(bytes);
    }

    /** POST /restore — restore system from a backup ZIP */
    @PostMapping("/restore")
    public ResponseEntity<ApiResponse<Map<String, Object>>> restore(
            @RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) return ResponseEntity.badRequest()
                .body(ApiResponse.error("No file provided"));
            if (!file.getOriginalFilename().endsWith(".zip")) return ResponseEntity.badRequest()
                .body(ApiResponse.error("File must be a .zip backup file"));

            Map<String, Object> result = backupService.restoreFromZip(file.getBytes());
            boolean ok = Boolean.TRUE.equals(result.get("success"));
            return ResponseEntity.ok(ok
                ? ApiResponse.ok("Restore complete", result)
                : ApiResponse.error("Restore completed with warnings — check result"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error("Restore failed: " + e.getMessage()));
        }
    }
}
