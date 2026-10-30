package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One day's performance note for one employee — a short rating + remark, entered by
 *  Admin/VP/OH/CEO, any number per employee per day. The employee can appeal within 48h of creation. The running history is what lets the
 *  employee profile show a performance trend over time, not just a single snapshot. */
@Entity
@Table(name = "hr_performance_remark", indexes = @Index(name = "idx_perf_remark_emp_date", columnList = "employee_id, remark_date"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceRemark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "remark_date", nullable = false)
    private LocalDate remarkDate;

    /** 1 (poor) to 5 (excellent); optional — a plain comment (e.g. from the HR dashboard) has none. */
    @Column(name = "rating")
    private Integer rating;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    /** Role of the reviewer who wrote it (VP / OH / CEO / ADMIN) — shown instead of their name. */
    @Column(name = "created_by_role", length = 30)
    private String createdByRole;

    /** Employee's appeal against this remark — only allowed within 48h of createdAt. */
    @Column(name = "appeal_text", length = 1000)
    private String appealText;

    @Column(name = "appealed_at")
    private LocalDateTime appealedAt;

    /** Reviewer who set/finalised the star rating after the appeal window. */
    @Column(name = "rated_by", length = 150)
    private String ratedBy;

    /** What was assessed, e.g. "Site handover", "BOQ accuracy". */
    @Column(name = "parameter", length = 200)
    private String parameter;

    /** KPI area — one of CATEGORY_WEIGHTS' keys; drives the score weighting. */
    @Column(name = "category", length = 30)
    private String category;

    /** Employee ticked (accepted) the comment instead of appealing — it can then be rated straight away. */
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    /** Appeal review: null (no appeal), PENDING, ACCEPTED (tick — withdrawn from score), REJECTED (cross — stands). */
    @Column(name = "appeal_status", length = 20)
    private String appealStatus;

    @Column(name = "decision_by", length = 150)
    private String decisionBy;

    /** Role of the reviewer who decided (VP / OH / CEO / ADMIN) — shown instead of their name. */
    @Column(name = "decision_role", length = 30)
    private String decisionRole;

    @Column(name = "decision_at")
    private LocalDateTime decisionAt;

    @Column(name = "decision_comment", length = 1000)
    private String decisionComment;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Comments before this date predate the accept/appeal/rate flow and are never queued for rating. */
    public static final LocalDateTime REVIEW_FLOW_START = LocalDateTime.of(2026, 10, 30, 0, 0);

    /** KPI categories and their weight (max score) — Score = Rating / 5 × weight. Weights total 100. */
    public static final java.util.Map<String, Integer> CATEGORY_WEIGHTS = new java.util.LinkedHashMap<>();
    static {
        CATEGORY_WEIGHTS.put("DELIVERY", 30);
        CATEGORY_WEIGHTS.put("QUALITY", 25);
        CATEGORY_WEIGHTS.put("ATTENDANCE", 15);
        CATEGORY_WEIGHTS.put("COMMUNICATION", 15);
        CATEGORY_WEIGHTS.put("TEAMWORK", 15);
    }

    @Transient
    public Integer getMaxScore() {
        return category == null ? null : CATEGORY_WEIGHTS.get(category);
    }

    /** Rating / 5 × category weight, one decimal; null when unrated/uncategorised or the appeal was accepted (withdrawn). */
    @Transient
    public Double getScore() {
        Integer w = getMaxScore();
        if (w == null || rating == null || "ACCEPTED".equals(appealStatus)) return null;
        return Math.round(rating / 5.0 * w * 10) / 10.0;
    }
}
