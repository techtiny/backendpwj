package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "bug_reports", indexes = {
    @Index(name = "idx_bug_status",      columnList = "status"),
    @Index(name = "idx_bug_severity",    columnList = "severity"),
    @Index(name = "idx_bug_module",      columnList = "module"),
    @Index(name = "idx_bug_reported_by", columnList = "reported_by"),
    @Index(name = "idx_bug_assigned_to", columnList = "assigned_to")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BugReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    // Vendors, Projects, HR, Procurement, Account, Other
    @Column(nullable = false, length = 30)
    private String module;

    // Low, Medium, High, Critical
    @Column(nullable = false, length = 20)
    private String severity;

    // Open, In Progress, Testing, Resolved, Closed
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "reported_by", nullable = false, length = 100)
    private String reportedBy;

    @Column(name = "reported_by_name", nullable = false, length = 150)
    private String reportedByName;

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @Column(name = "assigned_to_name", length = 150)
    private String assignedToName;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
