package com.pwj.tracker.account.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * One row of the Sales CRM "People" or "Companies" sheet.
 * kind = PERSON: name is the person, company is where they work.
 * kind = COMPANY: name is the company, contactName is the person to talk to.
 * connectBy is "Connect by" for a person and "Assignee" for a company.
 */
@Entity
@Table(name = "crm_contacts", indexes = @Index(name = "idx_crm_kind", columnList = "kind"))
@Data
public class CrmContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String kind;               // PERSON | COMPANY

    private String tag;                // Plan, High, Low, Estimate, Followup, Investment, Ongoing, Hot/Strategic

    @Column(nullable = false)
    private String name;

    private String company;            // PERSON only
    private String contactName;        // COMPANY only
    private String source;             // PERSON only
    private String title;
    private String contactType;        // Potential Customer | Current Customer | Other
    private String workPhone;
    private String connectBy;
    private String followedBy;
    private String status;             // Pending | Connected

    @Column(length = 2000)
    private String description;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
