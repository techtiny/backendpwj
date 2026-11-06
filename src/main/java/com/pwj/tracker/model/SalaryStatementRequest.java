package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * An employee's request for the salary statement (payslip) of one month. Statements are locked for everyone;
 * once an Admin / VP approves the request the employee can download that month's statement.
 * Kept as the issue log shown on the HR dashboard to VP / OH / CEO / Admin.
 */
@Entity
@Table(name = "hr_salary_statement_request")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryStatementRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "stmt_year", nullable = false)
    private Integer year;

    @Column(name = "stmt_month", nullable = false)
    private Integer month;

    /** PENDING, APPROVED, REJECTED */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "decided_by", length = 150)
    private String decidedBy;

    @Column(name = "decided_by_role", length = 30)
    private String decidedByRole;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(length = 500)
    private String comment;

    @Column(name = "download_count", nullable = false)
    @Builder.Default
    private Integer downloadCount = 0;

    @Column(name = "last_downloaded_at")
    private LocalDateTime lastDownloadedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
