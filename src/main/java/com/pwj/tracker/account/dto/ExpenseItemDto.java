package com.pwj.tracker.account.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ExpenseItemDto {
    private Long id;
    private Long projectId;
    private String category;
    private String description;
    private String partyName;
    private String monthYear;
    private String refNo;
    private BigDecimal pwjGross;
    private BigDecimal gstPercent;
    private BigDecimal pwjGstAmount;
    private BigDecimal pwjTotalPayable;
    private BigDecimal vendorGross;
    private BigDecimal vendorGstPercent;
    private BigDecimal vendorGstAmount;
    private BigDecimal vendorTotalPayable;
    private LocalDate paymentDate;
    private String paymentAgainst;
    private BigDecimal paidAmount;
    private BigDecimal balanceAsPerPwj;
    private BigDecimal balanceAsPerActual;
    private String paidTo;
    private String remarks;
    private List<String> allowedCategories;
    private Boolean eligibleForPayment;
    private String paymentStatus;
    private BigDecimal sentAmount;
    private java.time.LocalDateTime sentAt;
    private String ohApprovalStatus;
    private String vpApprovalStatus;
    private BigDecimal poValue;
    private BigDecimal poBalanceRemaining;
    private String projectName; // populated only by the cross-project "sent for payment" listing

    public ExpenseItemDto() {}

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
    public BigDecimal getBalanceAsPerPwj() { return balanceAsPerPwj; }
    public void setBalanceAsPerPwj(BigDecimal b) { this.balanceAsPerPwj = b; }
    public BigDecimal getBalanceAsPerActual() { return balanceAsPerActual; }
    public void setBalanceAsPerActual(BigDecimal b) { this.balanceAsPerActual = b; }
    public String getPaidTo() { return paidTo; }
    public void setPaidTo(String paidTo) { this.paidTo = paidTo; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public List<String> getAllowedCategories() { return allowedCategories; }
    public void setAllowedCategories(List<String> allowedCategories) { this.allowedCategories = allowedCategories; }
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
    public String getVpApprovalStatus() { return vpApprovalStatus; }
    public void setVpApprovalStatus(String vpApprovalStatus) { this.vpApprovalStatus = vpApprovalStatus; }
    public BigDecimal getPoValue() { return poValue; }
    public void setPoValue(BigDecimal poValue) { this.poValue = poValue; }
    public BigDecimal getPoBalanceRemaining() { return poBalanceRemaining; }
    public void setPoBalanceRemaining(BigDecimal poBalanceRemaining) { this.poBalanceRemaining = poBalanceRemaining; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
}
