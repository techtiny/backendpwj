package com.pwj.tracker.account.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** A festival / greeting message sent to a set of campaign contacts over WhatsApp. {name} in the message is replaced per contact. */
@Entity
@Table(name = "campaigns")
@Data
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** OH | VP | CEO — campaigns, like the contact lists, are kept per role. */
    @Column(name = "owner_role", nullable = false, length = 10)
    private String ownerRole;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 4000)
    private String message;

    /** DRAFT | SENDING | DONE | STOPPED */
    @Column(nullable = false, length = 10)
    private String status = "DRAFT";

    /** Festival greeting card (image with the Happizo logo) sent with the message as its caption — e.g. DIWALI. Null = text only. */
    @Column(length = 20)
    private String card;

    private String note;          // why it stopped, if it did
    private String createdBy;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
