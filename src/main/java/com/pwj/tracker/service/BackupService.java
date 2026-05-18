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

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Sends a full system backup every Saturday at 9:00 PM.
 * Attachments:
 *   1. PWJ-Backup-yyyy-MM-dd.xlsx  — all PWJ entries (Excel)
 *   2. PWJ-DB-Backup-yyyy-MM-dd.sql — JDBC-generated SQL dump (no mysqldump binary needed)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private final JavaMailSender     mailSender;
    private final ExcelExportService excelExportService;
    private final DataSource         dataSource;

    @Value("${pwj.report.email.from}")
    private String mailFrom;

    @Value("${pwj.backup.email.to:admin@happizo.com}")
    private String backupTo;

    public String getBackupTo() { return backupTo; }

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

    private void triggerBackupQuiet() {
        try { triggerBackup(); }
        catch (Exception e) { log.error("Weekly backup failed", e); }
    }

    // ── JDBC-based SQL dump (no mysqldump binary required) ───────────────────

    private byte[] generateDatabaseDump() throws Exception {
        StringWriter sw = new StringWriter();
        PrintWriter  pw = new PrintWriter(sw);

        try (Connection conn = dataSource.getConnection()) {
            String catalog = conn.getCatalog();
            DatabaseMetaData meta = conn.getMetaData();

            pw.println("-- PWJ Tracker Database Backup");
            pw.println("-- Generated: " + LocalDateTime.now());
            pw.println("-- Database: " + catalog);
            pw.println();
            pw.println("SET FOREIGN_KEY_CHECKS=0;");
            pw.println();

            List<String> tables = new ArrayList<>();
            try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) tables.add(rs.getString("TABLE_NAME"));
            }

            for (String table : tables) {
                pw.println("-- --------------------------------------------------------");
                pw.println("-- Table: " + table);
                pw.println("-- --------------------------------------------------------");

                // CREATE TABLE statement
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SHOW CREATE TABLE `" + table + "`")) {
                    if (rs.next()) {
                        pw.println("DROP TABLE IF EXISTS `" + table + "`;");
                        pw.println(rs.getString(2) + ";");
                        pw.println();
                    }
                }

                // Row data as INSERT statements
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT * FROM `" + table + "`")) {

                    ResultSetMetaData rsMeta = rs.getMetaData();
                    int colCount = rsMeta.getColumnCount();

                    while (rs.next()) {
                        StringBuilder sb = new StringBuilder("INSERT INTO `").append(table).append("` VALUES (");
                        for (int i = 1; i <= colCount; i++) {
                            if (i > 1) sb.append(", ");
                            Object val = rs.getObject(i);
                            if (val == null) {
                                sb.append("NULL");
                            } else if (val instanceof Number) {
                                sb.append(val);
                            } else if (val instanceof Boolean) {
                                sb.append(((Boolean) val) ? 1 : 0);
                            } else {
                                // Escape single quotes and backslashes
                                String s = val.toString()
                                        .replace("\\", "\\\\")
                                        .replace("'", "\\'")
                                        .replace("\n", "\\n")
                                        .replace("\r", "\\r");
                                sb.append("'").append(s).append("'");
                            }
                        }
                        sb.append(");");
                        pw.println(sb);
                    }
                }
                pw.println();
            }

            pw.println("SET FOREIGN_KEY_CHECKS=1;");
        }

        pw.flush();
        return sw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
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

        helper.addAttachment(
            "PWJ-Backup-" + dateSuffix + ".xlsx",
            new ByteArrayDataSource(excelBytes, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        );

        helper.addAttachment(
            "PWJ-DB-Backup-" + dateSuffix + ".sql",
            new ByteArrayDataSource(dbBytes, "application/sql")
        );

        mailSender.send(message);
    }
}
