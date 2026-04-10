package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "project")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200, unique = true)
    private String name;

    @Column(name = "location", length = 300)
    private String location;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // ── Client details ──────────────────────────────────────────────────────
    @Column(name = "client_name", length = 200)
    private String clientName;

    @Column(name = "client_gst_no", length = 50)
    private String clientGstNo;

    @Column(name = "client_address", columnDefinition = "TEXT")
    private String clientAddress;

    @Column(name = "billing_address", columnDefinition = "TEXT")
    private String billingAddress;

    // ── Financial ───────────────────────────────────────────────────────────
    @Column(name = "project_value", precision = 15, scale = 2)
    private BigDecimal projectValue;

    @Column(name = "gst_pct")
    private Integer gstPct;

    @Column(name = "total_value", precision = 15, scale = 2)
    private BigDecimal totalValue;

    // ── PO / WO ─────────────────────────────────────────────────────────────
    @Column(name = "po_wo_status", length = 20)
    private String poWoStatus;          // "Received" | "Pending"

    @Column(name = "po_wo_doc_url", length = 500)
    private String poWoDocUrl;

    // ── Amended PO / WO ─────────────────────────────────────────────────────
    @Column(name = "amended_po_wo_status", length = 20)
    private String amendedPoWoStatus;   // "Received" | "Pending" | "N/A"

    @Column(name = "amended_po_wo_doc_url", length = 500)
    private String amendedPoWoDocUrl;

    // ── Meta ────────────────────────────────────────────────────────────────
    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
