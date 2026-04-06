package com.pwj.tracker.service;

import com.pwj.tracker.dto.*;
import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.model.PwjEntry;
import com.pwj.tracker.model.Vendor;
import com.pwj.tracker.repository.AppUserRepository;
import com.pwj.tracker.repository.PwjEntryRepository;
import com.pwj.tracker.repository.VendorRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

    // ── Admin/Procurement: see all entries with filters ──────────────────
    public PagedResponse<PwjEntryResponse> getAll(
            String search, String status, String approval, String projectName,
            int page, int size, String sortBy, String sortDir) {

        PwjEntry.EntryStatus    statusEnum   = parseEnum(PwjEntry.EntryStatus.class,    status);
        PwjEntry.ApprovalStatus approvalEnum = parseEnum(PwjEntry.ApprovalStatus.class, approval);

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
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
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
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
                .vendor(req.getVendor())
                .pwjIssued(false)
                .status(PwjEntry.EntryStatus.OPEN)
                .remarks(req.getRemarks())
                .build();
        return toResponse(repository.save(entry));
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
        entry.setPwjIssued(req.getPwjIssued());
        entry.setPwjType(req.getPwjType());
        entry.setStatus(req.getStatus());
        entry.setDeliveredDate(req.getDeliveredDate());
        entry.setRemarks(req.getRemarks());
        return toResponse(repository.save(entry));
    }

    // ── Procurement update ────────────────────────────────────────────────
    @Transactional
    public PwjEntryResponse procurementUpdate(Long id, ProcurementUpdateRequest req) {
        PwjEntry entry = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found"));

        if (req.getVendor()    != null) entry.setVendor(req.getVendor());
        if (req.getPwjIssued() != null) entry.setPwjIssued(req.getPwjIssued());
        if (req.getStatus()    != null) entry.setStatus(req.getStatus());
        if (req.getDeliveredDate() != null) entry.setDeliveredDate(req.getDeliveredDate());
        if (req.getRemarks()   != null) entry.setRemarks(req.getRemarks());

        // PWJ Type: set and trigger vendor email if entry is already PROCEED
        if (req.getPwjType() != null && !req.getPwjType().isBlank()) {
            boolean isNewPwjType = !req.getPwjType().equals(entry.getPwjType());
            entry.setPwjType(req.getPwjType());
            if (isNewPwjType && PwjEntry.ApprovalStatus.PROCEED.equals(entry.getApprovalStatus())) {
                sendVendorEmail(entry);
            }
        }

        // Vendor Acknowledged: trigger engineer notification when flipped to true
        if (Boolean.TRUE.equals(req.getVendorAcknowledged())
                && !Boolean.TRUE.equals(entry.getVendorAcknowledged())) {
            entry.setVendorAcknowledged(true);
            entry.setVendorAcknowledgedAt(LocalDateTime.now());
            entry.setPwjIssued(true);   // auto-mark PWJ as issued
            sendEngineerNotification(entry);
        } else if (req.getVendorAcknowledged() != null) {
            entry.setVendorAcknowledged(req.getVendorAcknowledged());
            if (Boolean.FALSE.equals(req.getVendorAcknowledged())) {
                entry.setPwjIssued(false);  // auto-unmark PWJ when vendor unacknowledged
            }
        }

        return toResponse(repository.save(entry));
    }

    // ── Site Engineer: delivery doc update + notify procurement ──────────
    @Transactional
    public PwjEntryResponse deliveryUpdate(Long id, DeliveryUpdateRequest req) {
        PwjEntry entry = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        if (req.getStatus()        != null) entry.setStatus(req.getStatus());
        if (req.getDeliveredDate() != null) entry.setDeliveredDate(req.getDeliveredDate());
        if (req.getDeliveryDocUrl() != null && !req.getDeliveryDocUrl().isBlank()) {
            entry.setDeliveryDocUrl(req.getDeliveryDocUrl());
            sendProcurementNotification(entry, req.getUpdatedBy());
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
            entry.setPwjIssued(true);
            // If pwjType was already set by Procurement, now send vendor email
            if (entry.getPwjType() != null && !entry.getPwjType().isBlank()) {
                sendVendorEmail(entry);
            }
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
        return repository.findPendingApprovals()
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
            String year = String.valueOf(java.time.LocalDate.now().getYear());
            entry.setDocNumber(entry.getPwjType() + "-" + year + "-" + String.format("%04d", id));
        }
        entry.setDocStatus(PwjEntry.DocStatus.PENDING_VP_APPROVAL);
        return toResponse(repository.save(entry));
    }

    // ── VP: approve document ──────────────────────────────────────────────
    @Transactional
    public PwjEntryResponse approveDoc(Long id, String comment) {
        PwjEntry entry = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        entry.setDocStatus(PwjEntry.DocStatus.VP_APPROVED);
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

    // ── VP: list all entries pending document approval ────────────────────
    public List<PwjEntryResponse> getPendingDocApprovals() {
        return repository.findByDocStatus(PwjEntry.DocStatus.PENDING_VP_APPROVAL)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Email helpers ─────────────────────────────────────────────────────

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
            String subject = pwjLabel + " #" + entry.getId() + " — " + entry.getProjectName();
            String body = "Dear " + entry.getVendor() + ",\n\n" +
                    "We are pleased to inform you that a " + pwjLabel + " has been issued.\n\n" +
                    "Details:\n" +
                    "  Order #    : " + entry.getId() + "\n" +
                    "  Material   : " + entry.getMaterialRequired() + "\n" +
                    "  Project    : " + entry.getProjectName() + "\n" +
                    "  Quantity   : " + (entry.getQuantity() != null ? entry.getQuantity() : "—") + "\n" +
                    "  Required By: " + (entry.getDateOfRequirement() != null ? entry.getDateOfRequirement() : "—") + "\n\n" +
                    "Please acknowledge receipt of this " + pwjLabel + " at your earliest convenience.\n\n" +
                    "Regards,\nPWJ Construction Team";
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
            String subject = "Action Required: " + pwjLabel + " sent to Vendor — Entry #" + entry.getId();
            String body = "Dear " + entry.getRaisedBy() + ",\n\n" +
                    "This is to inform you that the " + pwjLabel + " for your material request has been acknowledged by the vendor.\n\n" +
                    "Details:\n" +
                    "  Entry #    : " + entry.getId() + "\n" +
                    "  Material   : " + entry.getMaterialRequired() + "\n" +
                    "  Project    : " + entry.getProjectName() + "\n" +
                    "  Vendor     : " + (entry.getVendor() != null ? entry.getVendor() : "—") + "\n" +
                    "  Order Type : " + pwjLabel + "\n\n" +
                    "Please monitor delivery and update the delivery status when material arrives.\n\n" +
                    "Regards,\nPWJ Procurement Team";
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
            String subject = "Delivery Documents Uploaded — Entry #" + entry.getId();
            String body = "Dear Procurement Team,\n\n" +
                    (updatedBy != null ? updatedBy : "Site Engineer") + " has uploaded delivery documents for:\n\n" +
                    "  Entry #         : " + entry.getId() + "\n" +
                    "  Material        : " + entry.getMaterialRequired() + "\n" +
                    "  Project         : " + entry.getProjectName() + "\n" +
                    "  Vendor          : " + (entry.getVendor() != null ? entry.getVendor() : "—") + "\n" +
                    "  Delivery Status : " + (entry.getStatus() == PwjEntry.EntryStatus.CLOSED ? "Delivered" : "Pending") + "\n" +
                    "  Delivered Date  : " + (entry.getDeliveredDate() != null ? entry.getDeliveredDate() : "—") + "\n\n" +
                    "Please log in to PWJ Tracker to review the uploaded documents.\n\n" +
                    "Regards,\nPWJ Tracker System";
            for (String email : emails) {
                sendEmail(email, subject, body);
            }
            log.info("Procurement notification sent to {} for entry #{}", emails, entry.getId());
        } catch (Exception e) {
            log.warn("Failed to send procurement notification for entry {}: {}", entry.getId(), e.getMessage());
        }
    }

    private void sendEmail(String to, String subject, String body) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(mailFrom);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, false);
        mailSender.send(message);
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
                .pwjIssued(e.getPwjIssued()).status(e.getStatus())
                .deliveredDate(e.getDeliveredDate()).remarks(e.getRemarks())
                .pwjType(e.getPwjType())
                .vendorAcknowledged(e.getVendorAcknowledged())
                .vendorAcknowledgedAt(e.getVendorAcknowledgedAt())
                .deliveryDocUrl(e.getDeliveryDocUrl())
                .approvalComment(e.getApprovalComment())
                .approvedBy(e.getApprovedBy()).approvedAt(e.getApprovedAt())
                .docNumber(e.getDocNumber()).docStatus(e.getDocStatus())
                .docComments(e.getDocComments())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .build();
    }

    private <T extends Enum<T>> T parseEnum(Class<T> clazz, String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("ALL")) return null;
        try { return Enum.valueOf(clazz, value.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
