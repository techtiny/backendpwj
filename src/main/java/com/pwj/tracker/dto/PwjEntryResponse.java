package com.pwj.tracker.dto;

import com.pwj.tracker.model.PwjEntry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// ============ Response DTO ============
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PwjEntryResponse {
    private Long id;
    private LocalDateTime timestamp;
    private String raisedBy;
    private String projectName;
    private String boqNo;
    private String materialRequired;
    private String specification;
    private String brand;
    private String unit;
    private BigDecimal quantity;
    private LocalDate dateOfRequirement;
    private String imageReference;
    // Dashboard column order: Image → Approval → Vendor → Type → PWJ → ACK → Delivered → Status → Dependency
    private PwjEntry.ApprovalStatus approvalStatus;
    private String vendor;
    private String pwjType;
    private Boolean pwjIssued;
    private Boolean ack;
    private LocalDate deliveredDate;
    private PwjEntry.EntryStatus status;
    private String dependency;
    private String remarks;
    private Boolean vendorAcknowledged;
    private LocalDateTime vendorAcknowledgedAt;
    private String deliveryDocUrl;
    private String approvalComment;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String docNumber;
    private PwjEntry.DocStatus docStatus;
    private String docComments;
    private String docData;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
