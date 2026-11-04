package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A salary advance an employee asks for, repaid in equal monthly instalments recovered from salary.
 * Flow: PENDING → (OH / VP approves, may adjust the term) APPROVED → (Admin transfers the money) PAID.
 * From the start month, each instalment shows up as an Advance deduction on the monthly Salary sheet.
 */
@Entity
@Table(name = "hr_salary_advance")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryAdvance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "raised_by_role", length = 50)
    private String raisedByRole;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String reason;

    /** Repayment term the employee asked for, in monthly instalments. */
    @Column(name = "requested_term_months", nullable = false)
    private Integer requestedTermMonths;

    /** Term actually applied — the approver can change it. Equal to the request until then. */
    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    // PENDING, APPROVED (awaiting transfer), PAID (repayment running), REJECTED, CANCELLED
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "approved_by", length = 150)
    private String approvedBy;

    @Column(name = "approved_by_role", length = 30)
    private String approvedByRole;

    @Column(name = "approval_comment", length = 500)
    private String approvalComment;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "paid_by", length = 150)
    private String paidBy;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** Payroll month (the sheet's year / month) of the first instalment — set when the advance is paid. */
    @Column(name = "start_year")
    private Integer startYear;

    @Column(name = "start_month")
    private Integer startMonth;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
