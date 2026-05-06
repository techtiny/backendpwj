package com.pwj.tracker.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportService {

    private final JavaMailSender mailSender;
    private final ExcelExportService excelExportService;

    @Value("${pwj.report.email.to}")
    private String reportTo;

    @Value("${pwj.report.email.from}")
    private String reportFrom;

    // Every Monday at 8:00 AM
    @Scheduled(cron = "0 0 8 * * MON")
    public void sendWeeklyReport() {
        log.info("Generating weekly PWJ report...");
        try {
            byte[] excelBytes = excelExportService.generateWeeklyReport();
            sendEmail(excelBytes);
            log.info("Weekly report sent to {}", reportTo);
        } catch (Exception e) {
            log.error("Failed to send weekly report", e);
        }
    }

    private void sendEmail(byte[] excelBytes) throws MessagingException {
        String date     = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
        String filename = "PWJ-Report-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".xlsx";

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setFrom(reportFrom);
        helper.setTo(reportTo.split(","));
        helper.setSubject("PWJ Weekly Report — " + date);
        helper.setText(
            "<html><body style='font-family:sans-serif'>" +
            "<h2 style='color:#0f4c81'>Procurement Tracker — Weekly Report</h2>" +
            "<p>Hi Team,</p>" +
            "<p>Please find attached the weekly Purchase Work Journal report for <b>" + date + "</b>.</p>" +
            "<p>The report includes all entries with their current approval and procurement status.</p>" +
            "<br/><p style='color:#64748b;font-size:12px'>This is an automated email sent every Monday at 8:00 AM.</p>" +
            "</body></html>",
            true
        );
        helper.addAttachment(filename, () -> new java.io.ByteArrayInputStream(excelBytes),
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        mailSender.send(message);
    }
}
