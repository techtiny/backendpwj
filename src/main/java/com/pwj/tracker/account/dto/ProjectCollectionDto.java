package com.pwj.tracker.account.dto;

import com.pwj.tracker.account.entity.ProjectCollection;
import java.math.BigDecimal;
import java.time.LocalDate;

public class ProjectCollectionDto {
    private Long id;
    private Long projectId;
    private String projectName;
    private ProjectCollection.Stage stage;
    private BigDecimal amount;
    private BigDecimal collectedAmt;
    private LocalDate collectedDate;
    private BigDecimal dueAmount;
    private LocalDate dueDate;
    private LocalDate paymentDate;
    private Integer paymentIntervalDays;
    private Boolean alertActive;

    public ProjectCollectionDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public ProjectCollection.Stage getStage() { return stage; }
    public void setStage(ProjectCollection.Stage stage) { this.stage = stage; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getCollectedAmt() { return collectedAmt; }
    public void setCollectedAmt(BigDecimal collectedAmt) { this.collectedAmt = collectedAmt; }
    public LocalDate getCollectedDate() { return collectedDate; }
    public void setCollectedDate(LocalDate collectedDate) { this.collectedDate = collectedDate; }
    public BigDecimal getDueAmount() { return dueAmount; }
    public void setDueAmount(BigDecimal dueAmount) { this.dueAmount = dueAmount; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }
    public Integer getPaymentIntervalDays() { return paymentIntervalDays; }
    public void setPaymentIntervalDays(Integer paymentIntervalDays) { this.paymentIntervalDays = paymentIntervalDays; }
    public Boolean getAlertActive() { return alertActive; }
    public void setAlertActive(Boolean alertActive) { this.alertActive = alertActive; }
}
