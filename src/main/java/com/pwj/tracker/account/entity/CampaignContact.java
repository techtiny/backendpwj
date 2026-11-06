package com.pwj.tracker.account.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** A WhatsApp campaign contact — the separate "Campaign list". Added by hand or moved over from the CRM People / Companies lists. */
@Entity
@Table(name = "campaign_contacts", uniqueConstraints = @UniqueConstraint(name = "uq_campaign_contact_role_phone", columnNames = {"owner_role", "phone"}))
@Data
public class CampaignContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Which list this belongs to — OH, VP and CEO each keep their own, shared by everyone holding that role. */
    @Column(name = "owner_role", nullable = false, length = 10)
    private String ownerRole;

    @Column(nullable = false)
    private String name;

    /** Digits only, with country code (e.g. 919876543210). */
    @Column(nullable = false, length = 20)
    private String phone;

    private String company;

    /** MANUAL | PERSON | COMPANY — where it was added from. */
    @Column(length = 10)
    private String origin = "MANUAL";

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
