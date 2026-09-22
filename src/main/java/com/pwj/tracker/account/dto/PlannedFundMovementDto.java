package com.pwj.tracker.account.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class PlannedFundMovementDto {
    private Long id;
    private String direction;       // INFLOW | OUTFLOW
    private LocalDate movementDate;
    private String party;
    private Long projectId;
    private String projectName;
    private BigDecimal amount;
    private String mode;
    private String sourceType;      // LOAN | CLIENT_PAYMENT | REFUND | OTHER
    private String referenceNo;     // Outflow: PWJ doc number. Inflow: Invoice No.
    private String remarks;
    private LocalDateTime createdAt;

    public PlannedFundMovementDto() {}

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
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
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
