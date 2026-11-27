package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A person paid a monthly salary from office / management expenses (e.g. VP, OH, CEO). The amount varies month to
 * month, so each SALARY line starts at 0 and is entered for that month. A SALARY line is created in {@link OfficeExpense} for every month from {@code startMonth} up to the current month.
 */
@Entity
@Table(name = "hr_office_staff")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfficeStaff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 60)
    private String designation;

    /** Unused — kept for the existing column; each month's amount is entered on its salary line. */
    @Column(name = "monthly_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal monthlyAmount;

    /** First month (YYYY-MM) a salary line is created for. */
    @Column(name = "start_month", nullable = false, length = 7)
    private String startMonth;

    @Column(nullable = false)
    private Boolean active;

    /** Employee Master record this person is linked to — bank details are fetched from it. */
    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "account_number", length = 30)
    private String accountNumber;

    @Column(length = 15)
    private String ifsc;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
