package com.pwj.tracker.model;

import jakarta.persistence.*;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

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

    @Column(name = "raised_by", length = 100)
    private String raisedBy;

    // Set only when an elevated role (Admin/VP/OH/Procurement) raises this entry on
    // behalf of the engineer/PM named in raisedBy — holds the actual submitter's name.
    @Column(name = "raised_by_proxy", length = 100)
    private String raisedByProxy;

    @Column(name = "project_name", length = 200)
    private String projectName;

    @Column(name = "boq_no", length = 50)
    private String boqNo;

    @Column(name = "material_required", length = 300)
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

    @Column(name = "image_reference", columnDefinition = "TEXT")
    private String imageReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", length = 20)
    private ApprovalStatus approvalStatus;

    @Column(name = "vendor", length = 200)
    private String vendor;

    @Builder.Default
    @Column(name = "pwj_issued", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private Boolean pwjIssued = false;

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

    @Builder.Default
    @Column(name = "vendor_acknowledged", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private Boolean vendorAcknowledged = false;

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

    // ── Account system link ───────────────────────────────────────────────
    @Column(name = "account_project_id")
    private Long projectId;

    // ── Document Workflow ─────────────────────────────────────────────────
    @Column(name = "doc_number", length = 30)
    private String docNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_status", length = 30)
    private DocStatus docStatus;

    @Column(name = "doc_comments", columnDefinition = "TEXT")
    private String docComments;

    @Column(name = "site_remarks", columnDefinition = "TEXT")
    private String siteRemarks;

    @Column(name = "doc_data", columnDefinition = "TEXT")
    private String docData;

    @Column(name = "dependency", length = 300)
    private String dependency;

    @PrePersist
    @PreUpdate
    void ensureDependency() {
        if (dependency == null || dependency.isBlank()) {
            dependency = "OH Approval";
        }
    }

    @Builder.Default
    @Column(name = "ack", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private Boolean ack = false;

    @Builder.Default
    @Column(name = "vendor_email_enabled", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private Boolean vendorEmailEnabled = false;

    // ── Test data isolation: entries created by a test login are flagged
    // and excluded from real users' views (and vice versa) ────────────────
    @Builder.Default
    @Column(name = "is_test_data", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private Boolean isTestData = false;

    // ── Visibility: lets the raising engineer share a PR with other
    // engineers instead of keeping it visible only to themselves ──────────
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 20, nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'PRIVATE'")
    private Visibility visibility = Visibility.PRIVATE;

    // ── Share this PR with specific named engineers (in addition to, or
    // instead of, the blanket "visible to all engineers" flag above) ──────
    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "pwj_entry_shared_engineers", joinColumns = @JoinColumn(name = "pwj_entry_id"))
    @Column(name = "engineer_name", length = 100)
    private Set<String> sharedWithEngineers = new HashSet<>();

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
        DRAFT, PENDING_VP_APPROVAL, VP_APPROVED, VP_REJECTED, REVISION_REQUESTED, REVOKED
    }

    public enum Visibility {
        PRIVATE, ENGINEERS
    }
}
