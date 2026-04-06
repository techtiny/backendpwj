package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "vendor")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vendor {

    public enum VendorStatus { PENDING_APPROVAL, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Vendor Details ──────────────────────────────────────────────────
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "gst_number", length = 20)
    private String gstNumber;

    @Column(name = "ratings")
    private Integer ratings;

    @Column(name = "contact_person", length = 150)
    private String contactPerson;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "tags", length = 255)
    private String tags;

    @Column(name = "vendor_doc_url", length = 500)
    private String vendorDocUrl;

    // ── Vendor Profile ──────────────────────────────────────────────────
    @Column(name = "vendor_code", length = 50)
    private String vendorCode;

    @Column(name = "website", length = 255)
    private String website;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "language", length = 50)
    private String language;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "zip_code", length = 20)
    private String zipCode;

    @Column(name = "street", length = 300)
    private String street;

    @Column(name = "bank_name", length = 200)
    private String bankName;

    @Column(name = "account_number", length = 30)
    private String accountNumber;

    @Column(name = "ifsc_code", length = 20)
    private String ifscCode;

    @Column(name = "bank_details", columnDefinition = "TEXT")
    private String bankDetails;

    @Column(name = "payment_details", columnDefinition = "TEXT")
    private String paymentDetails;

    @Column(name = "delivery_terms", length = 300)
    private String deliveryTerms;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Column(name = "same_address_for_billing_shipping")
    private Boolean sameAddressForBillingShipping;

    // ── Vendor Contacts (stored as JSON string) ─────────────────────────
    @Column(name = "contacts", columnDefinition = "TEXT")
    private String contacts;

    // ── Vendor Policies ─────────────────────────────────────────────────
    @Column(name = "maximum_return_days")
    private Integer maximumReturnDays;

    @Column(name = "return_fees")
    private String returnFees;

    @Column(name = "list_vendor_policies", columnDefinition = "TEXT")
    private String listVendorPolicies;

    @Column(name = "vendor_pays_return_shipping")
    private Boolean vendorPaysReturnShipping;

    // ── Status & Audit ──────────────────────────────────────────────────
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private VendorStatus status = VendorStatus.PENDING_APPROVAL;

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
