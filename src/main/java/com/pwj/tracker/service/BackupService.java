package com.pwj.tracker.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends a full system backup every Saturday at 9:00 PM to admin@happizo.com.
 * Attachments:
 *   1. PWJ-Backup-yyyy-MM-dd.xlsx  — all PWJ entries (Excel)
 *   2. PWJ-DB-Backup-yyyy-MM-dd.sql — MySQL database dump (mysqldump)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private final JavaMailSender     mailSender;
    private final ExcelExportService excelExportService;

    @Value("${pwj.report.email.from}")
    private String mailFrom;

    @Value("${pwj.backup.email.to:admin@happizo.com}")
    private String backupTo;

    public String getBackupTo() { return backupTo; }

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:root}")
    private String dbUsername;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    // Every Saturday at 21:00
    @Scheduled(cron = "0 0 21 * * SAT")
    public void sendWeeklyBackup() {
        triggerBackupQuiet();
    }

    /** Manual trigger — throws on failure so the caller can surface the error */
    public void triggerBackup() throws Exception {
        log.info("Starting weekly backup — {}", LocalDateTime.now());
        byte[] excelBytes = excelExportService.generateWeeklyReport();
        log.info("Excel backup generated: {} bytes", excelBytes.length);

        byte[] dbBytes = generateDatabaseDump();
        log.info("DB backup generated: {} bytes", dbBytes.length);

        sendBackupEmail(excelBytes, dbBytes);
        log.info("Weekly backup sent to {}", backupTo);
    }

    // Scheduled job — keeps silent failure so it doesn't crash the cron thread
    private void triggerBackupQuiet() {
        try { triggerBackup(); }
        catch (Exception e) { log.error("Weekly backup failed", e); }
    }

    // ── MySQL dump via mysqldump ──────────────────────────────────────────────

    private byte[] generateDatabaseDump() throws Exception {
        // Parse jdbc:mysql://HOST:PORT/DBNAME?...
        String host   = "localhost";
        int    port   = 3306;
        String dbName = "pwj_tracker";

        Pattern p = Pattern.compile("jdbc:mysql://([^:/]+)(?::(\\d+))?/([^?]+)");
        Matcher m = p.matcher(datasourceUrl);
        if (m.find()) {
            host   = m.group(1);
            port   = m.group(2) != null ? Integer.parseInt(m.group(2)) : 3306;
            dbName = m.group(3);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("mysqldump");
        cmd.add("-h"); cmd.add(host);
        cmd.add("-P"); cmd.add(String.valueOf(port));
        cmd.add("-u"); cmd.add(dbUsername);
        if (dbPassword != null && !dbPassword.isBlank()) {
            cmd.add("-p" + dbPassword);   // no space between -p and password
        }
        cmd.add("--single-transaction");
        cmd.add("--routines");
        cmd.add("--triggers");
        cmd.add(dbName);

        log.info("Running: mysqldump -h {} -P {} -u {} [password] --single-transaction {}", host, port, dbUsername, dbName);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream is = process.getInputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            // Read stderr for diagnostic
            String stderr = new String(process.getErrorStream().readAllBytes());
            log.warn("mysqldump exited with code {}. Stderr: {}", exitCode, stderr);
            // If mysqldump is unavailable, fall back to a simple notice file
            if (out.size() == 0) {
                String notice = "-- mysqldump not available on this host. Exit code: " + exitCode + "\n-- " + stderr;
                return notice.getBytes();
            }
        }
        return out.toByteArray();
    }

    // ── Email ─────────────────────────────────────────────────────────────────

    private void sendBackupEmail(byte[] excelBytes, byte[] dbBytes) throws MessagingException {
        String date       = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
        String dateSuffix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(mailFrom != null ? mailFrom : "noreply@happizo.com");
        helper.setTo((backupTo != null ? backupTo : "admin@happizo.com").split(","));
        helper.setSubject("Procurement Tracker — Weekly Backup " + date);
        helper.setText(
            "<html><body style='font-family:sans-serif;color:#0f172a'>" +
            "<h2 style='color:#0f4c81'>Procurement Tracker — Weekly System Backup</h2>" +
            "<p>Hi Admin,</p>" +
            "<p>Please find attached the automated weekly backup for <b>" + date + "</b>.</p>" +
            "<ul>" +
            "  <li><b>PWJ-Backup-" + dateSuffix + ".xlsx</b> — Full Excel export of all PWJ entries</li>" +
            "  <li><b>PWJ-DB-Backup-" + dateSuffix + ".sql</b> — MySQL database dump</li>" +
            "</ul>" +
            "<p>Store these files securely. This backup is generated every <b>Saturday at 9:00 PM</b>.</p>" +
            "<br/><p style='color:#64748b;font-size:12px'>This is an automated email from Procurement Tracker.</p>" +
            "</body></html>",
            true
        );

        // Excel attachment
        helper.addAttachment(
            "PWJ-Backup-" + dateSuffix + ".xlsx",
            new ByteArrayDataSource(excelBytes, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        );

        // SQL dump attachment
        helper.addAttachment(
            "PWJ-DB-Backup-" + dateSuffix + ".sql",
            new ByteArrayDataSource(dbBytes, "application/sql")
        );

        mailSender.send(message);
    }
}
