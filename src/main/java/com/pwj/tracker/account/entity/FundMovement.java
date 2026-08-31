package com.pwj.tracker.account.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single fund movement for Happizo Fund Management.
 *   INFLOW  — money received; {@code party} is the Source Type (an active project or a
 *             free-text value the user adds), {@code mode} is the Mode of Receipt.
 *   OUTFLOW — money paid out; {@code party} is the project it was paid to,
 *             {@code mode} is the Mode of Payment.
 */
@Entity
@Table(name = "fund_movements", indexes = @Index(name = "idx_fund_movement_dir", columnList = "direction"))
public class FundMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String direction;              // INFLOW | OUTFLOW

    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;

    /** Source Type (inflow) or Paid-to project (outflow) — display text. */
    @Column(nullable = false, length = 200)
    private String party;

    /** Set when {@code party} is a known project. */
    @Column(name = "project_id")
    private Long projectId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 60)
    private String mode;                   // Mode of Receipt / Payment

    @Column(length = 1000)
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public FundMovement() {}

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
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
