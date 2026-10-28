package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One day's performance note for one employee — a short rating + remark, entered by
 *  Admin/VP/OH/CEO, any number per employee per day. The employee can appeal within 24h of creation. The running history is what lets the
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

    /** Employee's appeal against this remark — only allowed within 24h of createdAt. */
    @Column(name = "appeal_text", length = 1000)
    private String appealText;

    @Column(name = "appealed_at")
    private LocalDateTime appealedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
