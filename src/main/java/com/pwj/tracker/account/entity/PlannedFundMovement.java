package com.pwj.tracker.account.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A forecasted (not yet actual) fund movement — Planned Inflow / Planned Outflow. Kept in a
 * table of its own, entirely separate from {@link FundMovement}, so Payment Funding's
 * available-fund calculation (which sums FundMovement only) can never be affected by planned
 * data — Payment Funding is based purely on Actual Inflow / Actual Outflow.
 */
@Entity
@Table(name = "planned_fund_movements", indexes = @Index(name = "idx_planned_fund_movement_dir", columnList = "direction"))
public class PlannedFundMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String direction;              // INFLOW | OUTFLOW

    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;

    /** Source Type free text (inflow) or Paid-to project (outflow) — same convention as FundMovement. */
    @Column(nullable = false, length = 200)
    private String party;

    /** Set when {@code party} is a known project. */
    @Column(name = "project_id")
    private Long projectId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 60)
    private String mode;

    // LOAN | CLIENT_PAYMENT | REFUND | OTHER
    @Column(name = "source_type", length = 30)
    private String sourceType;

    // Outflow: the PWJ/PO/WO/JO doc number. Inflow: the Invoice No.
    @Column(name = "reference_no", length = 100)
    private String referenceNo;

    @Column(length = 1000)
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public PlannedFundMovement() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public LocalDate getMovementDate() { return movementDate; }
    public void setMovementDate(LocalDate movementDate) { this.movementDate = movementDate; }
    public String getParty() { return party; }
    public void setParty(String party) { this.party = party; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
