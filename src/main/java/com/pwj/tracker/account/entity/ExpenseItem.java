package com.pwj.tracker.account.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "expense_items")
public class ExpenseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private String category;

    private String description;
    private String partyName;
    private String monthYear;
    private String refNo;

    @Column(precision = 15, scale = 2)
    private BigDecimal pwjGross = BigDecimal.ZERO;

    @Column(precision = 5, scale = 2)
    private BigDecimal gstPercent = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal pwjGstAmount = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal pwjTotalPayable = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal vendorGross = BigDecimal.ZERO;

    @Column(precision = 5, scale = 2)
    private BigDecimal vendorGstPercent = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal vendorGstAmount = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal vendorTotalPayable = BigDecimal.ZERO;

    private LocalDate paymentDate;
    private String paymentAgainst;

    @Column(precision = 15, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    private String paidTo;
    private String remarks;

    // Payment eligibility / send-for-payment workflow
    @Column(nullable = false)
    private Boolean eligibleForPayment = false;

    @Column(nullable = false)
    private String paymentStatus = "NOT_SENT"; // NOT_SENT, PART_PAYMENT_SENT, FULL_PAYMENT_SENT

    // Cumulative amount sent via the Send-for-Payment workflow. Tracked separately from
    // paidAmount so that Paid Amount / Balance to be paid / Paid To — all driven by
    // paidAmount — are never touched by Send for Payment.
    @Column(precision = 15, scale = 2)
    private BigDecimal sentAmount = BigDecimal.ZERO;

    // Timestamp of the most recent Send-for-Payment action — used only by the
    // "Send for Payment" tracker's timeline filter, separate from paymentDate.
    private java.time.LocalDateTime sentAt;

    // Three-stage approval on the sent amount, independent of the Send-for-Payment
    // workflow itself — OH reviews first (and may revise sentAmount), then Admin, then VP.
    @Column(nullable = false)
    private String ohApprovalStatus = "PENDING"; // PENDING, APPROVED, REJECTED

    @Column(nullable = false)
    private String adminApprovalStatus = "PENDING"; // PENDING, APPROVED, REJECTED

    @Column(nullable = false)
    private String vpApprovalStatus = "PENDING"; // PENDING, APPROVED, REJECTED

    // Deductions captured on the Send for Payment dashboard (during OH / Admin review).
    @Column(precision = 5, scale = 2)
    private BigDecimal tdsPercent;             // null / 1 / 2 / 10

    @Column(precision = 15, scale = 2)
    private BigDecimal tdsAmount;              // sentAmount * tdsPercent / 100

    @Column(nullable = false)
    private Boolean gstDeducted = false;       // GST yes/no

    @Column(precision = 15, scale = 2)
    private BigDecimal gstDeductionAmount;     // when gstDeducted: the PWJ doc GST amount

    @Column(precision = 15, scale = 2)
    private BigDecimal approvedValue;          // sentAmount - tdsAmount - gstDeductionAmount

    public ExpenseItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPartyName() { return partyName; }
    public void setPartyName(String partyName) { this.partyName = partyName; }
    public String getMonthYear() { return monthYear; }
    public void setMonthYear(String monthYear) { this.monthYear = monthYear; }
    public String getRefNo() { return refNo; }
    public void setRefNo(String refNo) { this.refNo = refNo; }
    public BigDecimal getPwjGross() { return pwjGross; }
    public void setPwjGross(BigDecimal pwjGross) { this.pwjGross = pwjGross; }
    public BigDecimal getGstPercent() { return gstPercent; }
    public void setGstPercent(BigDecimal gstPercent) { this.gstPercent = gstPercent; }
    public BigDecimal getPwjGstAmount() { return pwjGstAmount; }
    public void setPwjGstAmount(BigDecimal pwjGstAmount) { this.pwjGstAmount = pwjGstAmount; }
    public BigDecimal getPwjTotalPayable() { return pwjTotalPayable; }
    public void setPwjTotalPayable(BigDecimal pwjTotalPayable) { this.pwjTotalPayable = pwjTotalPayable; }
    public BigDecimal getVendorGross() { return vendorGross; }
    public void setVendorGross(BigDecimal vendorGross) { this.vendorGross = vendorGross; }
    public BigDecimal getVendorGstPercent() { return vendorGstPercent; }
    public void setVendorGstPercent(BigDecimal vendorGstPercent) { this.vendorGstPercent = vendorGstPercent; }
    public BigDecimal getVendorGstAmount() { return vendorGstAmount; }
    public void setVendorGstAmount(BigDecimal vendorGstAmount) { this.vendorGstAmount = vendorGstAmount; }
    public BigDecimal getVendorTotalPayable() { return vendorTotalPayable; }
    public void setVendorTotalPayable(BigDecimal vendorTotalPayable) { this.vendorTotalPayable = vendorTotalPayable; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }
    public String getPaymentAgainst() { return paymentAgainst; }
    public void setPaymentAgainst(String paymentAgainst) { this.paymentAgainst = paymentAgainst; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public String getPaidTo() { return paidTo; }
    public void setPaidTo(String paidTo) { this.paidTo = paidTo; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public Boolean getEligibleForPayment() { return eligibleForPayment; }
    public void setEligibleForPayment(Boolean eligibleForPayment) { this.eligibleForPayment = eligibleForPayment; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public BigDecimal getSentAmount() { return sentAmount; }
    public void setSentAmount(BigDecimal sentAmount) { this.sentAmount = sentAmount; }
    public java.time.LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(java.time.LocalDateTime sentAt) { this.sentAt = sentAt; }
    public String getOhApprovalStatus() { return ohApprovalStatus; }
    public void setOhApprovalStatus(String ohApprovalStatus) { this.ohApprovalStatus = ohApprovalStatus; }
    public String getAdminApprovalStatus() { return adminApprovalStatus; }
    public void setAdminApprovalStatus(String adminApprovalStatus) { this.adminApprovalStatus = adminApprovalStatus; }
    public String getVpApprovalStatus() { return vpApprovalStatus; }
    public void setVpApprovalStatus(String vpApprovalStatus) { this.vpApprovalStatus = vpApprovalStatus; }
    public BigDecimal getTdsPercent() { return tdsPercent; }
    public void setTdsPercent(BigDecimal tdsPercent) { this.tdsPercent = tdsPercent; }
    public BigDecimal getTdsAmount() { return tdsAmount; }
    public void setTdsAmount(BigDecimal tdsAmount) { this.tdsAmount = tdsAmount; }
    public Boolean getGstDeducted() { return gstDeducted; }
    public void setGstDeducted(Boolean gstDeducted) { this.gstDeducted = gstDeducted; }
    public BigDecimal getGstDeductionAmount() { return gstDeductionAmount; }
    public void setGstDeductionAmount(BigDecimal gstDeductionAmount) { this.gstDeductionAmount = gstDeductionAmount; }
    public BigDecimal getApprovedValue() { return approvedValue; }
    public void setApprovedValue(BigDecimal approvedValue) { this.approvedValue = approvedValue; }
}
