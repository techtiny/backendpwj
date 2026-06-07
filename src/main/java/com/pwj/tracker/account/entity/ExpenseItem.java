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
}
