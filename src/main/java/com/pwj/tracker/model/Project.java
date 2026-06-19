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

    @Column(name = "quote_value", precision = 15, scale = 2)
    private BigDecimal quoteValue;

    @Column(name = "quote_gst_pct")
    private Integer quoteGstPct;

    @Column(name = "quote_doc_url", length = 500)
    private String quoteDocUrl;

    @Column(name = "quote_total_value", precision = 15, scale = 2)
    private BigDecimal quoteTotalValue;

    @Column(name = "additional_wo_value", precision = 15, scale = 2)
    private BigDecimal additionalWoValue;

    @Column(name = "additional_wo_gst_pct")
    private Integer additionalWoGstPct;

    @Column(name = "additional_wo_total", precision = 15, scale = 2)
    private BigDecimal additionalWoTotal;

    @Column(name = "additional_wo_doc_url", length = 500)
    private String additionalWoDocUrl;

    @Column(name = "additional_quote_value", precision = 15, scale = 2)
    private BigDecimal additionalQuoteValue;

    @Column(name = "additional_quote_gst_pct")
    private Integer additionalQuoteGstPct;

    @Column(name = "additional_quote_total", precision = 15, scale = 2)
    private BigDecimal additionalQuoteTotal;

    @Column(name = "additional_quote_doc_url", length = 500)
    private String additionalQuoteDocUrl;

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

    @Builder.Default
    @Column(name = "eligible_for_accounts", nullable = false)
    private Boolean eligibleForAccounts = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
