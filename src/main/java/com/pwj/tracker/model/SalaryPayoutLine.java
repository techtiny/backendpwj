package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One employee's net pay for a released salary month — frozen at the moment the CEO releases the month, so the
 * amount the VP approves is exactly the amount on the bank file even if attendance changes afterwards. Bank details
 * are deliberately NOT stored here: they are read live from the Employee Master, so a missing account number that
 * HR fixes later is picked up without re-releasing the month.
 */
@Entity
@Table(name = "hr_salary_payout_line",
       uniqueConstraints = @UniqueConstraint(name = "uq_salary_payout_line", columnNames = {"year", "month", "user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryPayoutLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "month", nullable = false)
    private Integer month;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    /** Person code (employee number) at release time. */
    @Column(name = "person_code", length = 30)
    private String personCode;

    /** Take-home for the month — what gets transferred. */
    @Column(name = "net_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal netPay;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
