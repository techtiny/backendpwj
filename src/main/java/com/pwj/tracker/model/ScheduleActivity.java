package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * One activity row of a project schedule. Each row points at a BOQ line item
 * (by its BOQ No / label); a single BOQ item can have any number of activity
 * rows (e.g. framework, board installation, jointing for one ceiling item).
 */
@Entity
@Table(name = "ops_schedule_activity", indexes = @Index(name = "idx_ops_activity_project", columnList = "project_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** BOQ No of the linked BOQ line item; null for an activity not tied to the BOQ. */
    @Column(name = "boq_no", length = 50)
    private String boqNo;

    /** Snapshot of the BOQ item's description, so the row still reads right if the BOQ is replaced. */
    @Column(name = "boq_description", columnDefinition = "TEXT")
    private String boqDescription;

    @Column(name = "description", length = 500)
    private String description;

    /** Who this activity waits on: "Architect" | "Consultant" | "Client" | null (none). */
    @Column(name = "dependency", length = 20)
    private String dependency;

    @Column(name = "dependency_target_date")
    private LocalDate dependencyTargetDate;

    @Column(name = "material_delivery_date")
    private LocalDate materialDeliveryDate;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "planned_start")
    private LocalDate plannedStart;

    @Column(name = "planned_finish")
    private LocalDate plannedFinish;

    @Column(name = "actual_start")
    private LocalDate actualStart;

    @Column(name = "actual_finish")
    private LocalDate actualFinish;

    /** "COMPLETED" | "NOT_COMPLETED" | null (not marked yet). */
    @Column(name = "status", length = 20)
    private String status;

    /** Free-text note on the activity. */
    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
