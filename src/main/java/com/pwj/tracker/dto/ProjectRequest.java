package com.pwj.tracker.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectRequest {
    private String name;
    private String location;
    private String description;

    // Client
    private String clientName;
    private String clientGstNo;
    private String clientAddress;
    private String billingAddress;

    // Financial
    private BigDecimal projectValue;
    private Integer gstPct;

    // PO/WO
    private String poWoStatus;
    private String poWoDocUrl;

    // Amended PO/WO
    private String amendedPoWoStatus;
    private String amendedPoWoDocUrl;

    // For reactivation
    private Boolean active;
}
