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
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final JavaMailSender     mailSender;
    private final ExcelExportService excelExportService;
    private final DataSource         dataSource;

    @Value("${pwj.report.email.from}")
    private String mailFrom;

    @Value("${pwj.backup.email.to:admin@happizo.com}")
    private String backupTo;

    @Value("${pwj.upload.dir:uploads}")
    private String uploadDir;

    public String getBackupTo() { return backupTo; }

    // ── Scheduled: every Saturday at 21:00 IST ───────────────────────────────
    @Scheduled(cron = "0 0 21 * * SAT")
    public void sendWeeklyBackup() { triggerBackupQuiet(); }

    // ── Manual email trigger ──────────────────────────────────────────────────
    public void triggerBackup() throws Exception {
        log.info("Starting backup — {}", LocalDateTime.now(IST));
        byte[] zipBytes = generateFullBackupZip();
        log.info("Full backup ZIP generated: {} bytes", zipBytes.length);
        sendBackupEmail(zipBytes);
        log.info("Backup sent to {}", backupTo);
    }

    private void triggerBackupQuiet() {
        try { triggerBackup(); }
        catch (Exception e) { log.error("Weekly backup failed", e); }
    }

    // ── Full ZIP backup (SQL + Excel + uploads + metadata) ───────────────────
    public byte[] generateFullBackupZip() throws Exception {
        String dateSuffix = LocalDate.now(IST).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (ZipOutputStream zip = new ZipOutputStream(baos)) {
            zip.setLevel(Deflater.BEST_COMPRESSION);

            // 1. Metadata JSON
            String meta = String.format(
                "{\"backupDate\":\"%s\",\"version\":\"2.0\",\"system\":\"PWJ Tracker\",\"uploadDir\":\"%s\"}",
                LocalDateTime.now(IST), uploadDir.replace("\\", "/")
            );
            addEntry(zip, "metadata.json", meta.getBytes(StandardCharsets.UTF_8));

            // 2. SQL database dump (all tables — full restore capability)
            byte[] sql = generateDatabaseDump();
            addEntry(zip, "database/PWJ-DB-" + dateSuffix + ".sql", sql);

            // 3. Excel export (human-readable data)
            try {
                byte[] excel = excelExportService.generateWeeklyReport();
                addEntry(zip, "excel/PWJ-Data-" + dateSuffix + ".xlsx", excel);
            } catch (Exception e) {
                log.warn("Excel export skipped: {}", e.getMessage());
            }

            // 4. All uploaded files (images + documents)
            Path uploadsPath = Paths.get(uploadDir).toAbsolutePath();
            if (Files.exists(uploadsPath)) {
                Files.walk(uploadsPath)
                     .filter(Files::isRegularFile)
                     .forEach(file -> {
                         String rel = uploadsPath.relativize(file).toString().replace("\\", "/");
                         try {
                             addEntry(zip, "uploads/" + rel, Files.readAllBytes(file));
                         } catch (IOException ex) {
                             log.warn("Skipped file {}: {}", rel, ex.getMessage());
                         }
                     });
            } else {
                log.info("No uploads directory found at {}", uploadsPath);
            }
        }

        return baos.toByteArray();
    }

    private void addEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

    // ── Restore from ZIP ─────────────────────────────────────────────────────
    public Map<String, Object> restoreFromZip(byte[] zipBytes) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> restoredFiles = new ArrayList<>();
        boolean dbRestored = false;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            byte[] sqlToRestore = null;
            Map<String, byte[]> filesToRestore = new LinkedHashMap<>();

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) { zis.closeEntry(); continue; }
                byte[] data = zis.readAllBytes();
                String name = entry.getName();

                if (name.startsWith("database/") && name.endsWith(".sql")) {
                    sqlToRestore = data;
                } else if (name.startsWith("uploads/")) {
                    String rel = name.substring("uploads/".length());
                    if (!rel.isBlank()) filesToRestore.put(rel, data);
                }
                zis.closeEntry();
            }

            // Restore database
            if (sqlToRestore != null) {
                restoreDatabase(sqlToRestore);
                dbRestored = true;
                result.put("database", "Restored successfully");
            } else {
                result.put("database", "No SQL dump found in ZIP");
            }

            // Restore uploaded files
            Path uploadsPath = Paths.get(uploadDir).toAbsolutePath();
            Files.createDirectories(uploadsPath);
            for (Map.Entry<String, byte[]> e : filesToRestore.entrySet()) {
                Path target = uploadsPath.resolve(e.getKey()).normalize();
                if (!target.startsWith(uploadsPath)) continue; // path traversal guard
                Files.createDirectories(target.getParent());
                Files.write(target, e.getValue());
                restoredFiles.add(e.getKey());
            }
            result.put("filesRestored", restoredFiles.size());
            result.put("files", restoredFiles);
        }

        result.put("success", dbRestored);
        return result;
    }

    private void restoreDatabase(byte[] sqlBytes) throws Exception {
        String sql = new String(sqlBytes, StandardCharsets.UTF_8);
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Execute statement by statement
                StringBuilder stmt = new StringBuilder();
                for (String line : sql.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("--") || trimmed.isEmpty()) continue;
                    stmt.append(line).append("\n");
                    if (trimmed.endsWith(";")) {
                        String s = stmt.toString().trim();
                        if (!s.isEmpty()) {
                            try (Statement st = conn.createStatement()) {
                                st.execute(s);
                            }
                        }
                        stmt.setLength(0);
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // ── JDBC SQL dump ─────────────────────────────────────────────────────────
    private byte[] generateDatabaseDump() throws Exception {
        StringWriter sw = new StringWriter();
        PrintWriter pw  = new PrintWriter(sw);

        try (Connection conn = dataSource.getConnection()) {
            String catalog = conn.getCatalog();
            DatabaseMetaData meta = conn.getMetaData();

            pw.println("-- PWJ Tracker — Full Database Backup");
            pw.println("-- Generated: " + LocalDateTime.now(IST));
            pw.println("-- Database:  " + catalog);
            pw.println("-- Version:   2.0 (includes HR, Attendance, Leave, PettyCash, Vendors, Projects)");
            pw.println();
            pw.println("SET FOREIGN_KEY_CHECKS=0;");
            pw.println("SET NAMES utf8mb4;");
            pw.println();

            List<String> tables = new ArrayList<>();
            try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) tables.add(rs.getString("TABLE_NAME"));
            }

            for (String table : tables) {
                pw.println("-- ----------------------------------------------------------------");
                pw.println("-- Table: " + table);
                pw.println("-- ----------------------------------------------------------------");

                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SHOW CREATE TABLE `" + table + "`")) {
                    if (rs.next()) {
                        pw.println("DROP TABLE IF EXISTS `" + table + "`;");
                        pw.println(rs.getString(2) + ";");
                        pw.println();
                    }
                }

                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT * FROM `" + table + "`")) {
                    ResultSetMetaData rsMeta = rs.getMetaData();
                    int colCount = rsMeta.getColumnCount();
                    int rowCount = 0;
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
                                String s = val.toString()
                                        .replace("\\", "\\\\")
                                        .replace("'",  "\\'")
                                        .replace("\n", "\\n")
                                        .replace("\r", "\\r");
                                sb.append("'").append(s).append("'");
                            }
                        }
                        sb.append(");");
                        pw.println(sb);
                        rowCount++;
                    }
                    if (rowCount > 0) pw.println("-- " + rowCount + " row(s) in " + table);
                }
                pw.println();
            }

            pw.println("SET FOREIGN_KEY_CHECKS=1;");
        }

        pw.flush();
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ── Email ─────────────────────────────────────────────────────────────────
    private void sendBackupEmail(byte[] zipBytes) throws MessagingException {
        String date       = LocalDate.now(IST).format(DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
        String dateSuffix = LocalDate.now(IST).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(mailFrom != null ? mailFrom : "noreply@happizo.com");
        helper.setTo((backupTo != null ? backupTo : "admin@happizo.com").split(","));
        helper.setSubject("PWJ Tracker — Full System Backup " + date);
        helper.setText(
            "<html><body style='font-family:sans-serif;color:#0f172a'>" +
            "<h2 style='color:#0f4c81'>PWJ Tracker — Full System Backup</h2>" +
            "<p>Hi Admin,</p>" +
            "<p>Attached is the complete system backup for <b>" + date + "</b>.</p>" +
            "<h3>ZIP Contents:</h3>" +
            "<ul>" +
            "  <li><b>database/PWJ-DB-" + dateSuffix + ".sql</b> — Full MySQL dump (all tables: PWJ entries, HR, Attendance, Leave, Petty Cash, Vendors, Projects, Users)</li>" +
            "  <li><b>excel/PWJ-Data-" + dateSuffix + ".xlsx</b> — Human-readable Excel export</li>" +
            "  <li><b>uploads/</b> — All uploaded images and documents</li>" +
            "  <li><b>metadata.json</b> — Backup info and version</li>" +
            "</ul>" +
            "<h3>To Restore:</h3>" +
            "<ol>" +
            "  <li>Use the <b>Restore</b> button in the dashboard to upload this ZIP</li>" +
            "  <li>Or manually import the .sql file into MySQL to recover the database</li>" +
            "  <li>Copy the <b>uploads/</b> folder contents back to the server's upload directory</li>" +
            "</ol>" +
            "<p>Backup is generated automatically every <b>Saturday at 9:00 PM IST</b>.</p>" +
            "<br/><p style='color:#64748b;font-size:12px'>Automated backup — PWJ Tracker · Happizo Infrastructure</p>" +
            "</body></html>",
            true
        );

        helper.addAttachment(
            "PWJ-FullBackup-" + dateSuffix + ".zip",
            new ByteArrayDataSource(zipBytes, "application/zip")
        );

        mailSender.send(message);
    }
}
