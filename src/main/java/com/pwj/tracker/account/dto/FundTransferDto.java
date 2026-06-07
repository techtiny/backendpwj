package com.pwj.tracker.account.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class FundTransferDto {
    private Long id;
    private Long fromProjectId;
    private String fromProjectName;
    private Long toProjectId;
    private String toProjectName;
    private BigDecimal amount;
    private LocalDate transferDate;
    private String remarks;

    public FundTransferDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFromProjectId() { return fromProjectId; }
    public void setFromProjectId(Long fromProjectId) { this.fromProjectId = fromProjectId; }
    public String getFromProjectName() { return fromProjectName; }
    public void setFromProjectName(String fromProjectName) { this.fromProjectName = fromProjectName; }
    public Long getToProjectId() { return toProjectId; }
    public void setToProjectId(Long toProjectId) { this.toProjectId = toProjectId; }
    public String getToProjectName() { return toProjectName; }
    public void setToProjectName(String toProjectName) { this.toProjectName = toProjectName; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDate transferDate) { this.transferDate = transferDate; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
