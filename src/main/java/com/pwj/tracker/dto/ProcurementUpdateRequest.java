package com.pwj.tracker.dto;

import com.pwj.tracker.model.PwjEntry;
import lombok.*;
import java.time.LocalDate;

/**
 * Procurement can update: vendor, pwjType, pwjIssued, vendorAcknowledged, status, deliveredDate, remarks
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcurementUpdateRequest {
    private String pwjType;            // "PO", "WO", "JO"
    private String vendor;
    private Boolean pwjIssued;
    private Boolean vendorAcknowledged;
    private PwjEntry.EntryStatus status;
    private LocalDate deliveredDate;
    private String remarks;
}
