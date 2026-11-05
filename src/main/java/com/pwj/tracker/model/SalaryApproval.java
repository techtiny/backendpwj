package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Release approval for one month's salary sheet: Admin approves first, then OH and VP each approve on their own
 * (independent of each other), then the CEO gives the final approval — which releases the month (locks edits, enables payslips, notifies employees).
 * Any rejection, or any change to the sheet before release, sends the chain back to Admin.
 */
@Entity
@Table(name = "hr_salary_approval",
       uniqueConstraints = @UniqueConstraint(name = "uq_salary_approval", columnNames = {"year", "month"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "month", nullable = false)
    private Integer month;

    // Step 1 — Admin
    @Column(name = "admin_approved", nullable = false)
    @Builder.Default
    private Boolean adminApproved = false;
    @Column(name = "admin_by", length = 150)
    private String adminBy;
    @Column(name = "admin_at")
    private LocalDateTime adminAt;

    // Step 2a — OH, and 2b — VP: separate approvals, neither depends on the other
    @Column(name = "oh_approved", nullable = false)
    @Builder.Default
    private Boolean ohApproved = false;
    @Column(name = "oh_by", length = 150)
    private String ohBy;
    @Column(name = "oh_at")
    private LocalDateTime ohAt;

    @Column(name = "vp_approved", nullable = false)
    @Builder.Default
    private Boolean vpApproved = false;
    @Column(name = "vp_by", length = 150)
    private String vpBy;
    @Column(name = "vp_at")
    private LocalDateTime vpAt;

    // Legacy single "OH or VP" approval (before OH and VP became separate) — read only, to show old records
    @Column(name = "review_approved", nullable = false)
    @Builder.Default
    private Boolean reviewApproved = false;
    @Column(name = "review_by", length = 150)
    private String reviewBy;
    @Column(name = "review_role", length = 20)
    private String reviewRole;
    @Column(name = "review_at")
    private LocalDateTime reviewAt;

    // Step 3 — CEO (final)
    @Column(name = "ceo_approved", nullable = false)
    @Builder.Default
    private Boolean ceoApproved = false;
    @Column(name = "ceo_by", length = 150)
    private String ceoBy;
    @Column(name = "ceo_at")
    private LocalDateTime ceoAt;

    /** True once the CEO has approved — the month is locked and payslips are available. */
    @Column(name = "released", nullable = false)
    @Builder.Default
    private Boolean released = false;
    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    // Last rejection, kept so the sheet can say why it went back to Admin
    @Column(name = "rejected_by", length = 150)
    private String rejectedBy;
    @Column(name = "rejected_role", length = 20)
    private String rejectedRole;
    @Column(name = "reject_remark", length = 500)
    private String rejectRemark;
    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
