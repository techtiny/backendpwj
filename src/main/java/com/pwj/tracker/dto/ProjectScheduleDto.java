package com.pwj.tracker.dto;

import com.pwj.tracker.model.ScheduleActivity;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A project's whole schedule — header fields plus every activity row — loaded and saved in one go. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectScheduleDto {
    private Long projectId;
    private String woNumber;
    private LocalDate startDate;
    private LocalDate completionDate;
    private String spocClient;
    private String spocContact;
    private String happizoRepresentative;
    private String updatedBy;
    private LocalDateTime updatedAt;
    @Builder.Default
    private List<ScheduleActivity> activities = new ArrayList<>();
}
