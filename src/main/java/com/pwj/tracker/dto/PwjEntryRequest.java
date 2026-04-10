package com.pwj.tracker.dto;

import com.pwj.tracker.model.PwjEntry;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// ============ Create / Update Request ============
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PwjEntryRequest {

    private LocalDateTime timestamp;

    @NotBlank(message = "Raised by is required")
    private String raisedBy;

    @NotBlank(message = "Project name is required")
    private String projectName;

    private String boqNo;

    @NotBlank(message = "Material required is mandatory")
    private String materialRequired;

    private String specification;
    private String brand;
    private String unit;
    private BigDecimal quantity;
    private LocalDate dateOfRequirement;
    private String imageReference;
    private PwjEntry.ApprovalStatus approvalStatus;
    private String vendor;
    private Boolean pwjIssued;
    private String pwjType;            // "PO", "WO", "JO"
    private PwjEntry.EntryStatus status;
    private LocalDate deliveredDate;
    private String remarks;
    private String docData;
    private String docNumber;
    private String dependency;
}

// ============ Approval Action Request ============
class ApprovalRequestInner {
    private PwjEntry.ApprovalStatus approvalStatus;
    private String comment;
    private String approvedBy;
}
