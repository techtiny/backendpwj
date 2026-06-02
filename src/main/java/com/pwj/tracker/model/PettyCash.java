package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "hr_petty_cash", indexes = {
    @Index(name = "idx_pc_username", columnList = "username"),
    @Index(name = "idx_pc_date",     columnList = "expense_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PettyCash {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    // TRAVEL, FOOD, OFFICE_SUPPLIES, UTILITIES, MAINTENANCE, OTHERS
    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    // CASH, UPI, CARD, OTHER
    @Column(name = "payment_mode", length = 20)
    private String paymentMode;

    // PENDING, APPROVED, REJECTED
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_by_role", length = 50)
    private String approvedByRole;

    @Column(name = "approval_comment", length = 500)
    private String approvalComment;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
