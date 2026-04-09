package com.pwj.tracker.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pwj_entry", indexes = {
    @Index(name = "idx_project", columnList = "project_name"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_approval", columnList = "approval_status"),
    @Index(name = "idx_raised_by", columnList = "raised_by")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PwjEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @NotBlank(message = "Raised by is required")
    @Column(name = "raised_by", nullable = false, length = 100)
    private String raisedBy;

    @NotBlank(message = "Project name is required")
    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @Column(name = "boq_no", length = 50)
    private String boqNo;

    @NotBlank(message = "Material required is mandatory")
    @Column(name = "material_required", nullable = false, length = 300)
    private String materialRequired;

    @Column(name = "specification", columnDefinition = "TEXT")
    private String specification;

    @Column(name = "brand", length = 100)
    private String brand;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "quantity", precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(name = "date_of_requirement")
    private LocalDate dateOfRequirement;

    @Column(name = "image_reference", length = 500)
    private String imageReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", length = 20)
    private ApprovalStatus approvalStatus;

    @Column(name = "vendor", length = 200)
    private String vendor;

    @Column(name = "pwj_issued")
    private Boolean pwjIssued;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private EntryStatus status;

    @Column(name = "delivered_date")
    private LocalDate deliveredDate;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    // ── PWJ Workflow ─────────────────────────────────────────────────────
    @Column(name = "pwj_type", length = 5)
    private String pwjType;   // "PO", "WO", "JO"

    @Column(name = "vendor_acknowledged")
    private Boolean vendorAcknowledged;

    @Column(name = "vendor_acknowledged_at")
    private LocalDateTime vendorAcknowledgedAt;

    @Column(name = "delivery_doc_url", length = 500)
    private String deliveryDocUrl;

    @Column(name = "approval_comment", length = 500)
    private String approvalComment;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    // ── Document Workflow ─────────────────────────────────────────────────
    @Column(name = "doc_number", length = 30)
    private String docNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_status", length = 30)
    private DocStatus docStatus;

    @Column(name = "doc_comments", columnDefinition = "TEXT")
    private String docComments;

    @Column(name = "doc_data", columnDefinition = "TEXT")
    private String docData;

    @Column(name = "dependency", length = 300)
    private String dependency;

    @Column(name = "ack")
    private Boolean ack;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ---- Enums ----

    public enum ApprovalStatus {
        PROCEED, HOLD, NOT_APPROVED
    }

    public enum EntryStatus {
        OPEN, CLOSED
    }

    public enum DocStatus {
        DRAFT, PENDING_VP_APPROVAL, VP_APPROVED, VP_REJECTED, REVISION_REQUESTED
    }
}
