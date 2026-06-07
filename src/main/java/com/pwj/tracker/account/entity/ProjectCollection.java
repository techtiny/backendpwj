package com.pwj.tracker.account.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_collection")
public class ProjectCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 20)
    private Stage stage;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "collected_amt", precision = 15, scale = 2)
    private BigDecimal collectedAmt = BigDecimal.ZERO;

    @Column(name = "collected_date")
    private LocalDate collectedDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "payment_interval_days")
    private Integer paymentIntervalDays;

    @Column(name = "alert_active")
    private Boolean alertActive = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ProjectCollection() {}

    public enum Stage { ADVANCE, STAGE_1, STAGE_2, STAGE_3, FINAL }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Stage getStage() { return stage; }
    public void setStage(Stage stage) { this.stage = stage; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getCollectedAmt() { return collectedAmt; }
    public void setCollectedAmt(BigDecimal collectedAmt) { this.collectedAmt = collectedAmt; }
    public LocalDate getCollectedDate() { return collectedDate; }
    public void setCollectedDate(LocalDate collectedDate) { this.collectedDate = collectedDate; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }
    public Integer getPaymentIntervalDays() { return paymentIntervalDays; }
    public void setPaymentIntervalDays(Integer paymentIntervalDays) { this.paymentIntervalDays = paymentIntervalDays; }
    public Boolean getAlertActive() { return alertActive; }
    public void setAlertActive(Boolean alertActive) { this.alertActive = alertActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
