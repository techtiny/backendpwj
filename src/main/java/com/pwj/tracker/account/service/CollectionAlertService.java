package com.pwj.tracker.account.service;

import com.pwj.tracker.account.entity.ProjectCollection;
import com.pwj.tracker.account.repository.ProjectCollectionRepository;
import com.pwj.tracker.repository.ProjectRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class CollectionAlertService {

    private static final Logger log = LoggerFactory.getLogger(CollectionAlertService.class);
    private static final String ALERT_TO = "tshobana1@gmail.com";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final ProjectCollectionRepository collectionRepo;
    private final ProjectRepository projectRepo;
    private final JavaMailSender mailSender;

    public CollectionAlertService(ProjectCollectionRepository collectionRepo,
                                   ProjectRepository projectRepo,
                                   JavaMailSender mailSender) {
        this.collectionRepo = collectionRepo;
        this.projectRepo    = projectRepo;
        this.mailSender     = mailSender;
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void sendDailyAlerts() {
        LocalDate today = LocalDate.now();
        List<ProjectCollection> active = collectionRepo.findActiveAlerts(today);
        for (ProjectCollection c : active) {
            BigDecimal amount    = c.getAmount()       != null ? c.getAmount()       : BigDecimal.ZERO;
            BigDecimal collected = c.getCollectedAmt() != null ? c.getCollectedAmt() : BigDecimal.ZERO;
            if (collected.compareTo(amount) >= 0) continue;
            long daysLeft = ChronoUnit.DAYS.between(today, c.getPaymentDate());
            if (daysLeft >= 0 && daysLeft % 3 == 0) {
                String project = projectRepo.findById(c.getProjectId()).map(p -> p.getName()).orElse("Unknown Project");
                sendAlert(c, project, daysLeft, amount, collected);
            }
        }
    }

    public int triggerNow() {
        LocalDate today = LocalDate.now();
        List<ProjectCollection> all = collectionRepo.findAllWithPaymentDate();
        int sent = 0;
        for (ProjectCollection c : all) {
            BigDecimal amount    = c.getAmount()       != null ? c.getAmount()       : BigDecimal.ZERO;
            BigDecimal collected = c.getCollectedAmt() != null ? c.getCollectedAmt() : BigDecimal.ZERO;
            long daysLeft = ChronoUnit.DAYS.between(today, c.getPaymentDate());
            String project = projectRepo.findById(c.getProjectId()).map(p -> p.getName()).orElse("Unknown Project");
            sendAlert(c, project, daysLeft, amount, collected);
            sent++;
        }
        return sent;
    }

    private void sendAlert(ProjectCollection c, String project, long daysLeft,
                           BigDecimal amount, BigDecimal collected) {
        try {
            BigDecimal due      = amount.subtract(collected);
            String urgencyColor = daysLeft == 0 ? "#dc2626" : daysLeft <= 2 ? "#ea580c" : "#d97706";
            String urgencyLabel = daysLeft == 0 ? "DUE TODAY"
                    : daysLeft + " day" + (daysLeft == 1 ? "" : "s") + " remaining";
            String subject      = daysLeft == 0
                    ? "⚠️ Payment DUE TODAY — " + project + " · " + stageLabel(c.getStage())
                    : "🔔 " + daysLeft + " day" + (daysLeft == 1 ? "" : "s") + " left — " + project + " · " + stageLabel(c.getStage());

            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setTo(ALERT_TO);
            h.setSubject(subject);
            h.setText(buildHtml(urgencyColor, urgencyLabel, project, c, amount, collected, due), true);
            mailSender.send(msg);
            log.info("Collection alert sent: {} | {} | {} days left", project, c.getStage(), daysLeft);
        } catch (Exception ex) {
            log.error("Failed collection alert id={}: {}", c.getId(), ex.getMessage());
        }
    }

    private String buildHtml(String urgencyColor, String urgencyLabel, String project,
                              ProjectCollection c, BigDecimal amount, BigDecimal collected, BigDecimal due) {
        return "<div style=\"font-family:'Inter',sans-serif;max-width:560px;margin:0 auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,.08);\">"
             + "<div style=\"background:linear-gradient(135deg,#0f172a,#1e3a5f);padding:24px 28px;\">"
             + "<div style=\"font-size:20px;font-weight:800;color:#fff;\">Happizo CloudDesk</div>"
             + "<div style=\"font-size:13px;color:#94a3b8;margin-top:4px;\">Collection Payment Alert</div>"
             + "</div><div style=\"padding:28px;\">"
             + "<div style=\"background:" + urgencyColor + ";border-radius:10px;padding:14px 18px;margin-bottom:24px;text-align:center;\">"
             + "<div style=\"font-size:24px;font-weight:900;color:#fff;\">" + urgencyLabel + "</div></div>"
             + "<table style=\"width:100%;border-collapse:collapse;font-size:14px;\">"
             + row("Project", project) + row("Stage", stageLabel(c.getStage()))
             + row("Payment Due", c.getPaymentDate().format(FMT))
             + row("Stage Amount", "&#8377;" + String.format("%,.2f", amount))
             + row("Collected", "&#8377;" + String.format("%,.2f", collected))
             + "<tr><td style=\"padding:12px 0 4px;color:#64748b;font-weight:700;border-top:1px solid #f1f5f9;\">Balance Due</td>"
             + "<td style=\"font-size:18px;font-weight:900;color:#dc2626;padding-top:12px;border-top:1px solid #f1f5f9;\">&#8377;" + String.format("%,.2f", due) + "</td></tr>"
             + "</table></div></div>";
    }

    private String row(String label, String value) {
        return "<tr><td style=\"padding:9px 0;color:#64748b;width:140px;border-bottom:1px solid #f1f5f9;\">" + label + "</td>"
             + "<td style=\"font-weight:600;color:#0f172a;border-bottom:1px solid #f1f5f9;\">" + value + "</td></tr>";
    }

    private String stageLabel(ProjectCollection.Stage s) {
        return switch (s) {
            case ADVANCE -> "Advance";
            case STAGE_1 -> "Stage 1";
            case STAGE_2 -> "Stage 2";
            case STAGE_3 -> "Stage 3";
            case FINAL   -> "Final";
        };
    }
}
