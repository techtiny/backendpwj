package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A salary structure for one employee, effective from a date. Revisions (appraisals)
 * are new rows with a later effectiveFrom — the current structure for a month is the
 * row with the greatest effectiveFrom on or before that month.
 *
 * Only the monthly gross, the PF / PT flags and optional PF / PT amounts are stored; Basic (50%), HRA (12.5%)
 * and Other Allowance (37.5%) are derived from the gross.
 */
@Entity
@Table(name = "hr_employee_salary", indexes = {
        @Index(name = "idx_emp_salary_user", columnList = "user_id"),
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeSalary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "employee_number", length = 20)
    private String employeeNumber;

    @Column(name = "monthly_gross", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyGross;

    @Column(name = "pf_applicable", nullable = false)
    private Boolean pfApplicable = true;

    @Column(name = "pt_applicable", nullable = false)
    private Boolean ptApplicable = true;

    /** Monthly PF / PT amounts as set at define/revise time. Null = use the default
     *  (PF 12% of Basic, PT 208). Ignored when the matching *Applicable flag is off. */
    @Column(name = "pf_amount", precision = 12, scale = 2)
    private BigDecimal pfAmount;

    @Column(name = "pt_amount", precision = 12, scale = 2)
    private BigDecimal ptAmount;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** e.g. "Initial structure", "Appraisal FY26-27" */
    @Column(name = "note", length = 300)
    private String note;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
