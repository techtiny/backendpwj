package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "hr_leave_request")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String fullName;

    // CASUAL, SICK, EARNED, COMP_OFF, PERMISSION, HALF_DAY, OTHER
    @Column(name = "leave_type", nullable = false, length = 20)
    private String leaveType;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    // Whole days for normal leave, 0.5 for HALF_DAY, 0 for PERMISSION.
    @Column(name = "total_days", precision = 5, scale = 2)
    private BigDecimal totalDays;

    /** Hours requested (may be fractional, e.g. 1.5) — only set when leaveType = PERMISSION, always &lt;= 2. */
    @Column(name = "permission_hours", precision = 4, scale = 2)
    private BigDecimal permissionHours;

    /** Clock time range the employee is away — only set when leaveType = PERMISSION. e.g. "14:00" / "15:30". */
    @Column(name = "from_time", length = 5)
    private String fromTime;

    @Column(name = "to_time", length = 5)
    private String toTime;

    @Column(length = 1000)
    private String reason;

    // PENDING, APPROVED, REJECTED, CANCELLED
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approval_comment", length = 500)
    private String approvalComment;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
