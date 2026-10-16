package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Header of a project's Operations schedule (Project Planning tab): WO number,
 * dates and contact people. One per project; its activity rows live in
 * {@link ScheduleActivity}.
 */
@Entity
@Table(name = "ops_project_schedule")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, unique = true)
    private Long projectId;

    @Column(name = "wo_number", length = 100)
    private String woNumber;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "completion_date")
    private LocalDate completionDate;

    @Column(name = "spoc_client", length = 150)
    private String spocClient;

    @Column(name = "spoc_contact", length = 50)
    private String spocContact;

    @Column(name = "happizo_representative", length = 150)
    private String happizoRepresentative;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
