package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** Procurement → OEM Database: a brand / manufacturer contact for an item category (e.g. Plumbing · Sanitary fittings · Jaquar). */
@Entity
@Table(name = "oem_contact")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OemContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 120)
    private String category;

    @Column(length = 200)
    private String item;

    @Column(length = 150)
    private String brand;

    @Column(name = "contact_person", length = 150)
    private String contactPerson;

    @Column(length = 120)
    private String designation;

    @Column(name = "contact_number", length = 60)
    private String contactNumber;

    @Column(name = "contact_email", length = 200)
    private String contactEmail;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
