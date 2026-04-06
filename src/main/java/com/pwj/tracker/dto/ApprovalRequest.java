package com.pwj.tracker.dto;

import com.pwj.tracker.model.PwjEntry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {

    @NotNull(message = "Approval status is required")
    private PwjEntry.ApprovalStatus approvalStatus;

    private String comment;

    @NotBlank(message = "Approver name is required")
    private String approvedBy;
}
