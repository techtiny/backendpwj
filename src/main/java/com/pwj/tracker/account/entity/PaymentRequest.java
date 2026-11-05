package com.pwj.tracker.account.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A payment request against an issued PO / WO. The engineer who raised the PR requests it; the procurement person the PR
 * is allocated to approves or rejects it. Only an approved request becomes a real "sent for payment" entry
 * ({@link ExpenseItem}) that Accounts → Send for Payment picks up with its own approval levels.
 */
@Entity
@Table(name = "payment_request", indexes = @Index(name = "idx_payreq_entry", columnList = "pwj_entry_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pwj_entry_id", nullable = false)
    private Long pwjEntryId;

    /** Specific sub-doc (e.g. an Additional PO "PO-2627-0004A"); null = the entry's own document. */
    @Column(name = "doc_number", length = 60)
    private String docNumber;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String remarks;

    @Column(name = "payment_made_against", length = 60)
    private String paymentMadeAgainst;

    @Column(name = "payment_stage", length = 30)
    private String paymentStage;

    /** Login of the requesting engineer, and their display name. */
    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "requested_by_name", length = 150)
    private String requestedByName;

    /** PENDING (waiting for procurement), APPROVED (now in Send for Payment), REJECTED, CANCELLED. */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "decided_by", length = 150)
    private String decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_remark", length = 500)
    private String decisionRemark;

    /** The Send for Payment entry created on approval. */
    @Column(name = "expense_item_id")
    private Long expenseItemId;

    @CreationTimestamp
    @Column(name = "requested_at", updatable = false)
    private LocalDateTime requestedAt;
}
