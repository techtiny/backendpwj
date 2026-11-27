package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An employee's resignation. A VP / Admin first enables it for the employee (ENABLED); the employee then applies
 * (SUBMITTED) with a 2-month notice period, so the last working day is the resignation date + 2 months.
 * A VP / Admin can change the dates / reason and accept it (ACCEPTED) or mark it WITHDRAWN.
 * Disabling an unsubmitted one sets DISABLED. The final exit itself is still recorded on the Employee Exit page.
 */
@Entity
@Table(name = "hr_resignation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Resignation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(name = "employee_role", length = 30)
    private String employeeRole;

    /** ENABLED, SUBMITTED, ACCEPTED, WITHDRAWN, DISABLED */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "enabled_by", length = 150)
    private String enabledBy;

    @Column(name = "enabled_at")
    private LocalDateTime enabledAt;

    @Column(name = "resignation_date")
    private LocalDate resignationDate;

    @Column(name = "last_working_day")
    private LocalDate lastWorkingDay;

    @Column(name = "notice_months")
    private Integer noticeMonths;

    @Column(length = 1000)
    private String reason;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** VP / Admin note when changing / accepting / withdrawing */
    @Column(length = 1000)
    private String remarks;

    @Column(name = "updated_by", length = 150)
    private String updatedBy;

    @Column(name = "decided_by", length = 150)
    private String decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
