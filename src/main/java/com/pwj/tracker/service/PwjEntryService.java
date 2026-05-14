package com.pwj.tracker.service;

import com.pwj.tracker.dto.*;
import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.model.PwjEntry;
import com.pwj.tracker.model.Vendor;
import com.pwj.tracker.repository.AppUserRepository;
import com.pwj.tracker.repository.PwjEntryRepository;
import com.pwj.tracker.repository.VendorRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PwjEntryService {

    private final PwjEntryRepository repository;
    private final AppUserRepository  userRepository;
    private final VendorRepository   vendorRepository;
    private final JavaMailSender     mailSender;

    @Value("${pwj.report.email.from}")
    private String mailFrom;

    @Value("${pwj.newentry.email.to}")
    private String newEntryEmailTo;

    // ── Admin/Procurement: see all entries with filters ──────────────────
    public PagedResponse<PwjEntryResponse> getAll(
            String search, String status, String approval, String projectName,
            int page, int size, String sortBy, String sortDir) {

        PwjEntry.EntryStatus    statusEnum   = parseEnum(PwjEntry.EntryStatus.class,    status);
        PwjEntry.ApprovalStatus approvalEnum = parseEnum(PwjEntry.ApprovalStatus.class, approval);

        Sort.Order primary = sortDir.equalsIgnoreCase("desc")
                ? Sort.Order.desc(sortBy) : Sort.Order.asc(sortBy);
        Sort sort = Sort.by(primary, Sort.Order.desc("id"));
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<PwjEntry> pageResult = repository.findFiltered(
                (search == null || search.isBlank()) ? null : search,
                statusEnum, approvalEnum,
                (projectName == null || projectName.isBlank()) ? null : projectName,
                null, pageable);

        return buildPagedResponse(pageResult, page, size);
    }

    // ── Engineer: only their own entries ─────────────────────────────────
    public PagedResponse<PwjEntryResponse> getByEngineer(String raisedBy, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
        Page<PwjEntry> pageResult = repository.findFiltered(
                null, null, null, null, raisedBy, pageable);
        return buildPagedResponse(pageResult, page, size);
    }

    // ── Get single entry ─────────────────────────────────────────────────
    public PwjEntryResponse getById(Long id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("Entry not found with id: " + id));
    }

    // ── Create (Engineer raises request) ─────────────────────────────────
    @Transactional
    public PwjEntryResponse create(PwjEntryRequest req) {
        PwjEntry entry = PwjEntry.builder()
                .timestamp(LocalDateTime.now())
                .raisedBy(req.getRaisedBy())
                .projectName(req.getProjectName())
                .boqNo(req.getBoqNo())
                .materialRequired(req.getMaterialRequired())
                .specification(req.getSpecification())
                .brand(req.getBrand())
                .unit(req.getUnit())
                .quantity(req.getQuantity())
                .dateOfRequirement(req.getDateOfRequirement())
                .imageReference(req.getImageReference())
                .approvalStatus(PwjEntry.ApprovalStatus.HOLD)
                .vendor(req.getVendor() != null && !req.getVendor().isBlank() ? req.getVendor() : null)
                .pwjIssued(false)
                .ack(false)
                .vendorAcknowledged(false)
                .vendorEmailEnabled(false)
                .status(PwjEntry.EntryStatus.OPEN)
                .remarks(req.getRemarks())
                .dependency("OH Approval")
                .build();
        PwjEntryResponse saved = toResponse(repository.save(entry));
        CompletableFuture.runAsync(() -> sendNewEntryNotification(saved));
        return saved;
    }

    // ── Full update (Admin) ───────────────────────────────────────────────
    @Transactional
    public PwjEntryResponse update(Long id, PwjEntryRequest req) {
        PwjEntry entry = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        entry.setRaisedBy(req.getRaisedBy());
        entry.setProjectName(req.getProjectName());
        entry.setBoqNo(req.getBoqNo());
        entry.setMaterialRequired(req.getMaterialRequired());
        entry.setSpecification(req.getSpecification());
        entry.setBrand(req.getBrand());
        entry.setUnit(req.getUnit());
        entry.setQuantity(req.getQuantity());
        entry.setDateOfRequirement(req.getDateOfRequirement());
        entry.setApprovalStatus(req.getApprovalStatus());
        entry.setVendor(req.getVendor());
        // Only overwrite pwjIssued if explicitly provided — never let a null wipe it
        if (req.getPwjIssued() != null) entry.setPwjIssued(req.getPwjIssued());
        entry.setPwjType(req.getPwjType());
        entry.setDeliveredDate(req.getDeliveredDate());
        if (req.getDeliveredDate() != null) {
            entry.setStatus(PwjEntry.EntryStatus.CLOSED);
        } else {
            entry.setStatus(req.getStatus());
        }
        entry.setRemarks(req.getRemarks());
        if (req.getDocData() != null) entry.setDocData(req.getDocData());
        if (req.getDocNumber() != null && !req.getDocNumber().isBlank()) entry.setDocNumber(req.getDocNumber());
        if (req.getDependency() != null && !req.getDependency().isBlank()) entry.setDependency(req.getDependency());
        return toResponse(repository.save(entry));
    }

    // ── Procurement update ────────────────────────────────────────────────
    @Transactional
    public PwjEntryResponse procurementUpdate(Long id, ProcurementUpdateRequest req) {
        PwjEntry entry = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found"));

        if (req.getVendor()      != null) {
            if (!req.getVendor().isBlank() && entry.getApprovalStatus() != PwjEntry.ApprovalStatus.PROCEED) {
                throw new RuntimeException("Vendor cannot be assigned before OH approves this entry");
            }
            boolean vendorBeingAssigned = (entry.getVendor() == null || entry.getVendor().isBlank()) && !req.getVendor().isBlank();
            entry.setVendor(req.getVendor());
            if (vendorBeingAssigned) entry.setDependency("VP Approval");
        }
        if (req.getPwjIssued()   != null) entry.setPwjIssued(req.getPwjIssued());
        if (req.getStatus()      != null) entry.setStatus(req.getStatus());
        if (req.getDeliveredDate() != null) {
            entry.setDeliveredDate(req.getDeliveredDate());
            entry.setStatus(PwjEntry.EntryStatus.CLOSED);
        }
        if (req.getRemarks()     != null) entry.setRemarks(req.getRemarks());
        if (req.getDependency()  != null) entry.setDependency(req.getDependency());
        if (req.getAck()         != null) {
            entry.setAck(req.getAck());
            if (Boolean.TRUE.equals(req.getAck()) && !Boolean.TRUE.equals(entry.getAck())) {
                entry.setDependency("DIP");
            }
        }
        if (req.getDocData()     != null) entry.setDocData(req.getDocData());

        // PWJ Type: set and trigger vendor email if entry is already PROCEED
        if (req.getPwjType() != null && !req.getPwjType().isBlank()) {
            boolean isNewPwjType = !req.getPwjType().equals(entry.getPwjType());
            entry.setPwjType(req.getPwjType());
            if (isNewPwjType && PwjEntry.ApprovalStatus.PROCEED.equals(entry.getApprovalStatus())) {
                PwjEntry snapshot = entry;
                CompletableFuture.runAsync(() -> sendVendorEmail(snapshot));
            }
        }

        // Vendor Acknowledged: trigger engineer notification when flipped to true
        // Note: pwjIssued is NOT auto-set here — Procurement must tick it manually
        if (Boolean.TRUE.equals(req.getVendorAcknowledged())
                && !Boolean.TRUE.equals(entry.getVendorAcknowledged())) {
            entry.setVendorAcknowledged(true);
            entry.setVendorAcknowledgedAt(LocalDateTime.now());
            PwjEntry snap = entry;
            CompletableFuture.runAsync(() -> sendEngineerNotification(snap));
        } else if (req.getVendorAcknowledged() != null) {
            entry.setVendorAcknowledged(req.getVendorAcknowledged());
        }

        return toResponse(repository.save(entry));
    }

    // ── Site Engineer: delivery doc update + notify procurement ──────────
    @Transactional
    public PwjEntryResponse deliveryUpdate(Long id, DeliveryUpdateRequest req) {
        PwjEntry entry = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        if (req.getStatus()        != null) entry.setStatus(req.getStatus());
        if (req.getDeliveredDate() != null) {
            entry.setDeliveredDate(req.getDeliveredDate());
            entry.setStatus(PwjEntry.EntryStatus.CLOSED);
        }
        if (req.getDeliveryDocUrl() != null && !req.getDeliveryDocUrl().isBlank()) {
            entry.setDeliveryDocUrl(req.getDeliveryDocUrl());
            PwjEntry snap = entry;
            String updatedBy = req.getUpdatedBy();
            CompletableFuture.runAsync(() -> sendProcurementNotification(snap, updatedBy));
        }
        return toResponse(repository.save(entry));
    }

    // ── Approval (Admin only) ─────────────────────────────────────────────
    @Transactional
    public PwjEntryResponse updateApproval(Long id, ApprovalRequest req) {
        PwjEntry entry = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        entry.setApprovalStatus(req.getApprovalStatus());
        entry.setApprovalComment(req.getComment());
        entry.setApprovedBy(req.getApprovedBy());
        entry.setApprovedAt(LocalDateTime.now());
        if (req.getApprovalStatus() == PwjEntry.ApprovalStatus.PROCEED) {
            entry.setDependency("Procurement");
        }
        return toResponse(repository.save(entry));
    }

    // ── Delete (Admin) ────────────────────────────────────────────────────
    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new RuntimeException("Entry not found");
        repository.deleteById(id);
    }

    public List<String> getProjectNames() {
        return repository.findDistinctProjectNames();
    }

    public List<PwjEntryResponse> getPendingApprovals() {
        return repository.findByApprovalStatusInAndStatus(
                        List.of(PwjEntry.ApprovalStatus.HOLD, PwjEntry.ApprovalStatus.NOT_APPROVED),
                        PwjEntry.EntryStatus.OPEN)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Submit document for VP approval ───────────────────────────────────
    @Transactional
    public PwjEntryResponse submitDoc(Long id) {
        PwjEntry entry = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        if (entry.getVendor() == null || entry.getPwjType() == null) {
            throw new RuntimeException("Vendor and PWJ Type must be assigned before generating a document");
        }
        if (entry.getDocNumber() == null) {
            java.time.LocalDate today = java.time.LocalDate.now();
            int month = today.getMonthValue();
            int year  = today.getYear();
            int fyStart = month >= 4 ? year : year - 1;
            String fy = String.format("%02d%02d", fyStart % 100, (fyStart + 1) % 100);
            entry.setDocNumber(entry.getPwjType() + "-" + fy + "-" + String.format("%04d", id));
        }
        entry.setDocStatus(PwjEntry.DocStatus.PENDING_VP_APPROVAL);
        entry.setDependency("VP Approval");
        return toResponse(repository.save(entry));
    }

    // ── VP: approve document ──────────────────────────────────────────────
    @Transactional
    public PwjEntryResponse approveDoc(Long id, String comment) {
        PwjEntry entry = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        entry.setDocStatus(PwjEntry.DocStatus.VP_APPROVED);
        entry.setDependency("Procurement");
        if (comment != null && !comment.isBlank()) entry.setDocComments(comment.trim());
        return toResponse(repository.save(entry));
    }

    // ── VP: reject document ───────────────────────────────────────────────
    @Transactional
    public PwjEntryResponse rejectDoc(Long id, String comment) {
        PwjEntry entry = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        if (comment != null && !comment.isBlank()) {
            entry.setDocComments(comment.trim());
            entry.setDocStatus(PwjEntry.DocStatus.REVISION_REQUESTED);
        } else {
            entry.setDocStatus(PwjEntry.DocStatus.VP_REJECTED);
        }
        return toResponse(repository.save(entry));
    }

    // ── VP/Admin: toggle vendor email enabled ─────────────────────────────
    @Transactional
    public PwjEntryResponse toggleVendorEmail(Long id) {
        PwjEntry entry = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        entry.setVendorEmailEnabled(!Boolean.TRUE.equals(entry.getVendorEmailEnabled()));
        return toResponse(repository.save(entry));
    }

    // ── VP: list all entries pending document approval ────────────────────
    public List<PwjEntryResponse> getPendingDocApprovals() {
        return repository.findByDocStatus(PwjEntry.DocStatus.PENDING_VP_APPROVAL)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Email helpers ─────────────────────────────────────────────────────

    private void sendNewEntryNotification(PwjEntryResponse entry) {
        try {
            String[] recipients = newEntryEmailTo.split(",");
            String subject = "📋 New Material Request — " + entry.getProjectName() + " (#" + entry.getId() + ")";
            String body = htmlEmail("New Material Request",
                    "A new material request has been raised and is pending your approval.",
                    new String[][]{
                        {"Entry #",        String.valueOf(entry.getId())},
                        {"Raised By",      entry.getRaisedBy()},
                        {"Project",        entry.getProjectName()},
                        {"BOQ No.",        entry.getBoqNo() != null ? entry.getBoqNo() : "—"},
                        {"Material",       entry.getMaterialRequired()},
                        {"Specification",  entry.getSpecification() != null ? entry.getSpecification() : "—"},
                        {"Brand",          entry.getBrand() != null ? entry.getBrand() : "—"},
                        {"Unit",           entry.getUnit() != null ? entry.getUnit() : "—"},
                        {"Quantity",       entry.getQuantity() != null ? String.valueOf(entry.getQuantity()) : "—"},
                        {"Required By",    entry.getDateOfRequirement() != null ? entry.getDateOfRequirement().toString() : "—"},
                    },
                    "Please log in to Procurement Tracker to review and approve this request.", "#1a6ab1");
            for (String to : recipients) {
                sendEmail(to.trim(), subject, body);
            }
            log.info("New entry notification sent to {} for entry #{}", newEntryEmailTo, entry.getId());
        } catch (Exception e) {
            log.warn("Failed to send new entry notification for entry {}: {}", entry.getId(), e.getMessage());
        }
    }

    private void sendVendorEmail(PwjEntry entry) {
        try {
            // Look up vendor email by name
            Optional<Vendor> vendorOpt = vendorRepository.findByNameAndActiveTrue(entry.getVendor());
            if (vendorOpt.isEmpty() || vendorOpt.get().getEmail() == null || vendorOpt.get().getEmail().isBlank()) {
                log.warn("Cannot send vendor email for entry {}: vendor email not found for '{}'", entry.getId(), entry.getVendor());
                return;
            }
            String vendorEmail = vendorOpt.get().getEmail();
            String pwjLabel = pwjTypeLabel(entry.getPwjType());
            String subject = "📦 " + pwjLabel + " #" + entry.getId() + " — " + entry.getProjectName();
            String body = htmlEmail(pwjLabel + " Issued",
                    "Dear " + entry.getVendor() + ", we are pleased to inform you that a <strong>" + pwjLabel + "</strong> has been issued.",
                    new String[][]{
                        {"Order #",      String.valueOf(entry.getId())},
                        {"Material",     entry.getMaterialRequired()},
                        {"Project",      entry.getProjectName()},
                        {"Quantity",     entry.getQuantity() != null ? String.valueOf(entry.getQuantity()) : "—"},
                        {"Required By",  entry.getDateOfRequirement() != null ? entry.getDateOfRequirement().toString() : "—"},
                    },
                    "Please acknowledge receipt of this " + pwjLabel + " at your earliest convenience.", "#166534");
            sendEmail(vendorEmail, subject, body);
            log.info("Vendor email sent to {} for entry #{}", vendorEmail, entry.getId());
        } catch (Exception e) {
            log.warn("Failed to send vendor email for entry {}: {}", entry.getId(), e.getMessage());
        }
    }

    private void sendEngineerNotification(PwjEntry entry) {
        try {
            Optional<AppUser> engOpt = userRepository.findByFullNameAndActiveTrue(entry.getRaisedBy());
            if (engOpt.isEmpty() || engOpt.get().getEmail() == null || engOpt.get().getEmail().isBlank()) {
                log.warn("Cannot send engineer notification for entry {}: engineer email not found for '{}'", entry.getId(), entry.getRaisedBy());
                return;
            }
            String to = engOpt.get().getEmail();
            String pwjLabel = pwjTypeLabel(entry.getPwjType());
            String subject = "✅ " + pwjLabel + " Acknowledged by Vendor — Entry #" + entry.getId();
            String body = htmlEmail("Vendor Acknowledged Your Request",
                    "Dear " + entry.getRaisedBy() + ", the <strong>" + pwjLabel + "</strong> for your material request has been acknowledged by the vendor.",
                    new String[][]{
                        {"Entry #",     String.valueOf(entry.getId())},
                        {"Material",    entry.getMaterialRequired()},
                        {"Project",     entry.getProjectName()},
                        {"Vendor",      entry.getVendor() != null ? entry.getVendor() : "—"},
                        {"Order Type",  pwjLabel},
                    },
                    "Please monitor the delivery and update the delivery status when the material arrives.", "#059669");
            sendEmail(to, subject, body);
            log.info("Engineer notification sent to {} for entry #{}", to, entry.getId());
        } catch (Exception e) {
            log.warn("Failed to send engineer notification for entry {}: {}", entry.getId(), e.getMessage());
        }
    }

    private void sendProcurementNotification(PwjEntry entry, String updatedBy) {
        try {
            List<AppUser> procUsers = userRepository.findByRoleAndActiveTrue(AppUser.Role.PROCUREMENT);
            List<String> emails = procUsers.stream()
                    .map(AppUser::getEmail)
                    .filter(e -> e != null && !e.isBlank())
                    .collect(Collectors.toList());
            if (emails.isEmpty()) {
                log.warn("No procurement users with email found for notification of entry {}", entry.getId());
                return;
            }
            String subject = "🚚 Delivery Documents Uploaded — Entry #" + entry.getId();
            String body = htmlEmail("Delivery Documents Uploaded",
                    (updatedBy != null ? updatedBy : "Site Engineer") + " has uploaded delivery documents for the following entry.",
                    new String[][]{
                        {"Entry #",          String.valueOf(entry.getId())},
                        {"Material",         entry.getMaterialRequired()},
                        {"Project",          entry.getProjectName()},
                        {"Vendor",           entry.getVendor() != null ? entry.getVendor() : "—"},
                        {"Delivery Status",  entry.getStatus() == PwjEntry.EntryStatus.CLOSED ? "Delivered" : "Pending"},
                        {"Delivered Date",   entry.getDeliveredDate() != null ? entry.getDeliveredDate().toString() : "—"},
                    },
                    "Please log in to Procurement Tracker to review the uploaded documents.", "#7c3aed");
            for (String email : emails) {
                sendEmail(email, subject, body);
            }
            log.info("Procurement notification sent to {} for entry #{}", emails, entry.getId());
        } catch (Exception e) {
            log.warn("Failed to send procurement notification for entry {}: {}", entry.getId(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public void sendVendorDoc(Long id, String htmlContent) throws IOException, MessagingException {
        PwjEntry entry = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found: " + id));
        if (!"VP_APPROVED".equals(entry.getDocStatus() != null ? entry.getDocStatus().name() : "")) {
            throw new RuntimeException("Document must be VP approved before sending to vendor");
        }
        Optional<Vendor> vendorOpt = vendorRepository.findByNameAndActiveTrue(entry.getVendor());
        if (vendorOpt.isEmpty() || vendorOpt.get().getEmail() == null || vendorOpt.get().getEmail().isBlank()) {
            throw new RuntimeException("Vendor email not found for: " + entry.getVendor());
        }
        String vendorEmail = vendorOpt.get().getEmail();
        String pwjLabel    = pwjTypeLabel(entry.getPwjType());
        String subject     = "📄 " + pwjLabel + " — " + entry.getProjectName();
        String emailBody = htmlEmail(pwjLabel + " Document",
                "Dear " + entry.getVendor() + ", please find the approved <strong>" + pwjLabel + "</strong> document attached.",
                new String[][]{
                    {"Order #",      String.valueOf(entry.getId())},
                    {"Material",     entry.getMaterialRequired()},
                    {"Project",      entry.getProjectName()},
                    {"Quantity",     entry.getQuantity() != null ? String.valueOf(entry.getQuantity()) : "—"},
                    {"Required By",  entry.getDateOfRequirement() != null ? entry.getDateOfRequirement().toString() : "—"},
                },
                "Kindly acknowledge receipt and proceed as discussed.", "#166534");

        byte[] pdfBytes = generatePdfFromHtml(htmlContent);
        String filename = pwjLabel.replace(" ", "_") + "_" + (entry.getDocNumber() != null ? entry.getDocNumber() : entry.getId()) + ".pdf";

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(mailFrom);
        helper.setTo(vendorEmail);
        helper.setCc(new String[]{"anusha@happizo.com", "bharath@happizo.com", "sunil@happizo.com"});
        helper.setSubject(subject);
        helper.setText(emailBody, true);
        helper.addAttachment(filename, new ByteArrayDataSource(pdfBytes, "application/pdf"));
        mailSender.send(message);
        log.info("Vendor doc email sent to {} (cc: anusha, bharath, sunil) for entry #{}", vendorEmail, id);
    }

    private byte[] generatePdfFromHtml(String html) throws IOException {
        Document jsoupDoc = Jsoup.parse(html);
        jsoupDoc.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml);
        String xhtml = jsoupDoc.html();
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        }
    }

    private void sendEmail(String to, String subject, String body) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(mailFrom);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, true); // true = HTML
        mailSender.send(message);
    }

    private String htmlEmail(String title, String intro, String[][] rows, String footer, String accentColor) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>")
          .append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
          .append("</head><body style='margin:0;padding:0;background:#f0f4f8;font-family:Arial,Helvetica,sans-serif;'>")
          .append("<table width='100%' cellpadding='0' cellspacing='0' style='background:#f0f4f8;padding:40px 16px;'>")
          .append("<tr><td align='center'>")
          .append("<table width='600' cellpadding='0' cellspacing='0' style='max-width:600px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 8px 40px rgba(0,0,0,0.10);'>")

          // ── Top logo bar ──
          .append("<tr><td style='background:#ffffff;padding:24px 36px 18px;border-bottom:1px solid #e8f0f9;'>")
          .append("<table width='100%' cellpadding='0' cellspacing='0'><tr>")
          .append("<td><img src='https://happizo.com/assets/myimages/logo.png' alt='Happizo' width='80' style='display:block;max-width:80px;height:auto;'/></td>")
          .append("<td align='right' style='vertical-align:middle;'>")
          .append("<div style='font-size:11px;color:#94a3b8;letter-spacing:1.5px;text-transform:uppercase;font-weight:600;'>Procurement Tracker</div>")
          .append("<div style='font-size:10px;color:#b0bec5;margin-top:2px;'>Purchase Work Journal System</div>")
          .append("</td></tr></table>")
          .append("</td></tr>")

          // ── Coloured header band ──
          .append("<tr><td style='background:linear-gradient(135deg,").append(accentColor).append(",").append(accentColor).append("cc);padding:32px 36px;'>")
          .append("<div style='font-size:10px;font-weight:700;color:rgba(255,255,255,0.7);letter-spacing:2.5px;text-transform:uppercase;margin-bottom:8px;'>HAPPIZO INFRASTRUCTURE AND SOLUTIONS</div>")
          .append("<div style='font-size:24px;font-weight:800;color:#ffffff;letter-spacing:-0.3px;'>").append(title).append("</div>")
          .append("</td></tr>")

          // ── Intro ──
          .append("<tr><td style='padding:28px 36px 20px;font-size:14px;color:#475569;line-height:1.7;'>").append(intro).append("</td></tr>")

          // ── Details table ──
          .append("<tr><td style='padding:0 36px 28px;'>")
          .append("<table width='100%' cellpadding='0' cellspacing='0' style='border-radius:10px;overflow:hidden;border:1px solid #e2e8f0;font-size:13px;'>");
        for (int i = 0; i < rows.length; i++) {
            String bg = i % 2 == 0 ? "#f8fafc" : "#ffffff";
            sb.append("<tr style='background:").append(bg).append(";'>")
              .append("<td style='padding:11px 18px;font-size:11.5px;font-weight:700;color:#64748b;width:38%;border-bottom:1px solid #e8f0f9;text-transform:uppercase;letter-spacing:0.5px;'>").append(rows[i][0]).append("</td>")
              .append("<td style='padding:11px 18px;font-size:13px;color:#0f172a;font-weight:500;border-bottom:1px solid #e8f0f9;'>").append(rows[i][1]).append("</td>")
              .append("</tr>");
        }
        sb.append("</table></td></tr>")

          // ── Footer note ──
          .append("<tr><td style='padding:0 36px 28px;'>")
          .append("<div style='background:#f0f9ff;border-left:4px solid ").append(accentColor).append(";border-radius:0 8px 8px 0;padding:14px 18px;font-size:13px;color:#475569;line-height:1.6;'>")
          .append(footer)
          .append("</div></td></tr>")

          // ── Signature ──
          .append("<tr><td style='padding:0 36px 32px;'>")
          .append("<table width='100%' cellpadding='0' cellspacing='0' style='border-top:1px dashed #e2e8f0;padding-top:20px;margin-top:4px;'>")
          .append("<tr>")
          .append("<td style='vertical-align:top;'>")
          .append("<div style='font-size:13px;font-weight:700;color:#1e293b;margin-bottom:2px;'>HAPPIZO INFRASTRUCTURE AND SOLUTIONS</div>")
          .append("<div style='font-size:11.5px;color:#64748b;line-height:1.7;'>")
          .append("Old #11, New #20, II cross st., Indira Ng., Adyar, Ch-20.<br/>")
          .append("&#128222; +91-9360900042<br/>")
          .append("&#127758; <a href='http://www.happizo.com' style='color:#3b82f6;text-decoration:none;'>www.happizo.com</a>")
          .append("</div>")
          .append("</td>")
          .append("<td align='right' style='vertical-align:middle;'>")
          .append("<img src='https://happizo.com/assets/myimages/logo.png' alt='Happizo' width='56' style='display:block;max-width:56px;height:auto;opacity:0.85;'/>")
          .append("</td>")
          .append("</tr></table>")
          .append("</td></tr>")

          // ── Bottom disclaimer ──
          .append("<tr><td style='background:#f8fafc;padding:14px 36px;border-top:1px solid #e2e8f0;border-radius:0 0 16px 16px;'>")
          .append("<div style='font-size:10.5px;color:#94a3b8;line-height:1.6;'>")
          .append("This is an automated notification from <strong style='color:#64748b;'>Procurement Tracker</strong>. Please do not reply to this email.")
          .append("</div>")
          .append("</td></tr>")

          .append("</table></td></tr></table></body></html>");
        return sb.toString();
    }

    private String pwjTypeLabel(String pwjType) {
        if (pwjType == null) return "Order";
        return switch (pwjType) {
            case "PO" -> "Purchase Order";
            case "WO" -> "Work Order";
            case "JO" -> "Job Work Order";
            default   -> pwjType;
        };
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private PagedResponse<PwjEntryResponse> buildPagedResponse(Page<PwjEntry> p, int page, int size) {
        return PagedResponse.<PwjEntryResponse>builder()
                .content(p.getContent().stream().map(this::toResponse).collect(Collectors.toList()))
                .page(page).size(size)
                .totalElements(p.getTotalElements()).totalPages(p.getTotalPages())
                .first(p.isFirst()).last(p.isLast())
                .totalClosed(repository.countByStatus(PwjEntry.EntryStatus.CLOSED))
                .totalOpen(repository.countByStatus(PwjEntry.EntryStatus.OPEN))
                .totalProceed(repository.countByApprovalStatus(PwjEntry.ApprovalStatus.PROCEED))
                .totalHold(repository.countByApprovalStatus(PwjEntry.ApprovalStatus.HOLD))
                .totalNotApproved(repository.countByApprovalStatus(PwjEntry.ApprovalStatus.NOT_APPROVED))
                .build();
    }

    private PwjEntryResponse toResponse(PwjEntry e) {
        return PwjEntryResponse.builder()
                .id(e.getId()).timestamp(e.getTimestamp())
                .raisedBy(e.getRaisedBy()).projectName(e.getProjectName())
                .boqNo(e.getBoqNo()).materialRequired(e.getMaterialRequired())
                .specification(e.getSpecification()).brand(e.getBrand())
                .unit(e.getUnit()).quantity(e.getQuantity())
                .dateOfRequirement(e.getDateOfRequirement())
                .imageReference(e.getImageReference())
                .approvalStatus(e.getApprovalStatus()).vendor(e.getVendor())
                .pwjIssued(Boolean.TRUE.equals(e.getPwjIssued())).status(e.getStatus())
                .deliveredDate(e.getDeliveredDate()).remarks(e.getRemarks())
                .pwjType(e.getPwjType())
                .vendorAcknowledged(Boolean.TRUE.equals(e.getVendorAcknowledged()))
                .vendorAcknowledgedAt(e.getVendorAcknowledgedAt())
                .deliveryDocUrl(e.getDeliveryDocUrl())
                .approvalComment(e.getApprovalComment())
                .approvedBy(e.getApprovedBy()).approvedAt(e.getApprovedAt())
                .docNumber(e.getDocNumber()).docStatus(e.getDocStatus())
                .docComments(e.getDocComments())
                .docData(e.getDocData())
                .dependency(e.getDependency())
                .ack(Boolean.TRUE.equals(e.getAck()))
                .vendorEmailEnabled(Boolean.TRUE.equals(e.getVendorEmailEnabled()))
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .build();
    }

    private <T extends Enum<T>> T parseEnum(Class<T> clazz, String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("ALL")) return null;
        try { return Enum.valueOf(clazz, value.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
