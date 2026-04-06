package com.pwj.tracker.dto;

import com.pwj.tracker.model.PwjEntry;
import lombok.*;
import java.time.LocalDate;

/** Site Engineer updates delivery status + uploads delivery document */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryUpdateRequest {
    private PwjEntry.EntryStatus status;   // OPEN = Pending, CLOSED = Delivered
    private LocalDate deliveredDate;
    private String deliveryDocUrl;
    private String updatedBy;              // engineer full name (for email notification)
}
