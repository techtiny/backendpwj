package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * HR overrides for one employee's salary in one month — Sunday/extra working days that
 * add pay back, a manual LOP or working-day figure for joiners / exits / special cases,
 * a remarks note, and a "finalized" lock. Absent row ⇒ the sheet is fully auto-computed.
 */
@Entity
@Table(name = "hr_salary_month_adjustment",
       uniqueConstraints = @UniqueConstraint(name = "uq_salary_adj", columnNames = {"user_id", "year", "month"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryMonthAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "month", nullable = false)
    private Integer month;

    /** Days added back to working days — e.g. Sunday / holiday working. */
    @Column(name = "extra_working_days", precision = 6, scale = 2)
    private BigDecimal extraWorkingDays;

    /** Overrides the leave-derived loss-of-pay days when set. */
    @Column(name = "manual_lop_days", precision = 6, scale = 2)
    private BigDecimal manualLopDays;

    /** Full override of working days (joiners / mid-month exits / waivers) when set. */
    @Column(name = "manual_working_days", precision = 6, scale = 2)
    private BigDecimal manualWorkingDays;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "finalized", nullable = false)
    private Boolean finalized = false;

    @Column(name = "updated_by", length = 150)
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
