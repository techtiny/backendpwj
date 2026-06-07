package com.pwj.tracker.account.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fund_transfers")
public class FundTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long fromProjectId;

    @Column(nullable = false)
    private Long toProjectId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    private LocalDate transferDate;
    private String remarks;

    public FundTransfer() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFromProjectId() { return fromProjectId; }
    public void setFromProjectId(Long fromProjectId) { this.fromProjectId = fromProjectId; }
    public Long getToProjectId() { return toProjectId; }
    public void setToProjectId(Long toProjectId) { this.toProjectId = toProjectId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDate transferDate) { this.transferDate = transferDate; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
