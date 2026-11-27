package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An office / management expense. kind EXPENSE is raised by a VP / Admin; kind SALARY is the monthly line created
 * automatically for each {@link OfficeStaff}. Both appear in Accounts → Weekly Payments → Office/Mgnt, where they are
 * marked PAID.
 */
@Entity
@Table(name = "hr_office_expense",
       uniqueConstraints = @UniqueConstraint(name = "uk_office_salary_month", columnNames = { "staff_id", "period_month" }))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfficeExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** EXPENSE or SALARY */
    @Column(nullable = false, length = 10)
    private String kind;

    /** Month the line belongs to, YYYY-MM. */
    @Column(name = "period_month", nullable = false, length = 7)
    private String periodMonth;

    @Column(name = "expense_date")
    private LocalDate expenseDate;

    @Column(length = 60)
    private String category;

    @Column(length = 1000)
    private String description;

    @Column(length = 150)
    private String payee;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** Beneficiary bank details for the bank payment file (SALARY lines copy them from the staff member). */
    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "account_number", length = 30)
    private String accountNumber;

    @Column(length = 15)
    private String ifsc;

    /** Set on SALARY lines. */
    @Column(name = "staff_id")
    private Long staffId;

    /** JSON array of uploaded file URLs. */
    @Column(name = "attachment_urls", columnDefinition = "TEXT")
    private String attachmentUrls;

    /** PENDING or PAID */
    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "raised_by", length = 150)
    private String raisedBy;

    @Column(name = "paid_by", length = 150)
    private String paidBy;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "payment_ref", length = 100)
    private String paymentRef;

    @Column(length = 1000)
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
