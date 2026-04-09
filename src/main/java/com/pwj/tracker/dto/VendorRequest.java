package com.pwj.tracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VendorRequest {

    // ── Vendor Details ──────────────────────────────────────────
    @NotBlank(message = "Company name is required")
    private String name;

    private String gstNumber;
    private Integer ratings;
    private String contactPerson;
    private String phoneNumber;
    private String email;
    private String category;
    private String tags;
    private String vendorDocUrl;
    private String companyType;
    private String vendorType;
    private String spocName;
    private String spocEmail;
    private String spocPhone;
    private String branch;
    private String empanelDate;
    private String productServices;
    private String socialMedia;
    private String panNumber;
    private String tanNumber;
    private String cinNumber;
    private String msmeNumber;
    private String gstDocUrl;
    private String msmeDocUrl;
    private String tanDocUrl;
    private String panDocUrl;

    // ── Vendor Profile ──────────────────────────────────────────
    private String vendorCode;
    private String website;
    private String currency;
    private String language;
    private String country;
    private String state;
    private String city;
    private String zipCode;
    private String street;
    private String bankName;
    private String accountNumber;
    private String ifscCode;
    private String bankDetails;
    private String bankDocUrl;
    private String paymentDetails;
    private String deliveryTerms;
    private String joiningDate;        // "YYYY-MM-DD" string from frontend
    private Boolean sameAddressForBillingShipping;

    // ── Vendor Contacts (list of {personName, role, contactNumber, email}) ──
    private List<Map<String, String>> contacts;

    // ── Vendor Policies ─────────────────────────────────────────
    private String maximumReturnDays;
    private String returnFees;
    private String listVendorPolicies;
    private Boolean vendorPaysReturnShipping;

    // ── Workflow status ─────────────────────────────────────────
    private String status;             // "PENDING_APPROVAL" | "APPROVED"

   
}
