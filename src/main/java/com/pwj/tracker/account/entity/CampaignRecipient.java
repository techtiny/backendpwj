package com.pwj.tracker.account.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "campaign_recipients", indexes = @Index(name = "idx_campaign_recipient_campaign", columnList = "campaignId"))
@Data
public class CampaignRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long campaignId;

    // Snapshot — still correct if the contact is edited or deleted later.
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, length = 20)
    private String phone;

    /** PENDING | SENT | FAILED */
    @Column(nullable = false, length = 10)
    private String status = "PENDING";

    @Column(length = 500)
    private String error;

    private LocalDateTime sentAt;
}
