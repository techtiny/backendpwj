package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * An issued revision of a project's Operations schedule (Rev 0, Rev 1, …): a frozen copy of the header
 * and every activity row at the moment it was issued, kept as JSON so old revisions stay viewable and
 * printable exactly as they were even after the working schedule changes.
 */
@Entity
@Table(name = "ops_schedule_revision",
        uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "revision_no"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "revision_no", nullable = false)
    private Integer revisionNo;

    /** Why the schedule was revised. */
    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "snapshot", columnDefinition = "LONGTEXT")
    private String snapshot;
}
