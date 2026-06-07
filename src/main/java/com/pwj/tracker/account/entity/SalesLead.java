package com.pwj.tracker.account.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales_leads")
public class SalesLead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;
    private String client;
    private String contactPerson;
    private String contactPhone;
    private String contactEmail;

    @Column(nullable = false)
    private String stage;

    private String source;
    private String businessType;
    private String location;
    private String description;
    private String notes;

    @Column(precision = 15, scale = 2)
    private BigDecimal dealValue = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal quoteValue;

    private Integer probabilityPct;
    private LocalDate expectedCloseDate;
    private LocalDate actualCloseDate;
    private String assignedTo;
    private String fy;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }

    public SalesLead() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getClient() { return client; }
    public void setClient(String client) { this.client = client; }
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public BigDecimal getDealValue() { return dealValue; }
    public void setDealValue(BigDecimal dealValue) { this.dealValue = dealValue; }
    public BigDecimal getQuoteValue() { return quoteValue; }
    public void setQuoteValue(BigDecimal quoteValue) { this.quoteValue = quoteValue; }
    public Integer getProbabilityPct() { return probabilityPct; }
    public void setProbabilityPct(Integer probabilityPct) { this.probabilityPct = probabilityPct; }
    public LocalDate getExpectedCloseDate() { return expectedCloseDate; }
    public void setExpectedCloseDate(LocalDate expectedCloseDate) { this.expectedCloseDate = expectedCloseDate; }
    public LocalDate getActualCloseDate() { return actualCloseDate; }
    public void setActualCloseDate(LocalDate actualCloseDate) { this.actualCloseDate = actualCloseDate; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public String getFy() { return fy; }
    public void setFy(String fy) { this.fy = fy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
