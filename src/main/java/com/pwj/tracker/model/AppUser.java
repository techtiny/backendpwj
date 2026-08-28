package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_user", indexes = {
    @Index(name = "idx_username", columnList = "username", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "designation", length = 150)
    private String designation;

    @Column(name = "employee_number", length = 20)
    private String employeeNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private Role role;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    // ── Employee exit ("marked exited from Happizo") ──────────────────────
    // The employee can no longer log in (active=false) but every record they
    // created stays in the system. These fields capture the exit for HR.
    @Column(name = "exited", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    @Builder.Default
    private Boolean exited = false;

    @Column(name = "exit_date")
    private LocalDate exitDate;              // last working day

    @Column(name = "exit_type", length = 30)
    private String exitType;                 // RESIGNED | TERMINATED | ABSCONDED | RETIRED | OTHER

    @Column(name = "exit_reason", columnDefinition = "TEXT")
    private String exitReason;

    @Column(name = "exit_marked_by", length = 150)
    private String exitMarkedBy;             // username / full name of the Admin or VP who marked it

    @Column(name = "exit_marked_at")
    private LocalDateTime exitMarkedAt;

    @Builder.Default
    @Column(name = "is_test_account", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private Boolean isTestAccount = false;

    @Column(name = "session_token", length = 64)
    private String sessionToken;

    @Column(name = "session_created_at")
    private LocalDateTime sessionCreatedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Role {
        ADMIN, ENGINEER, PROCUREMENT, VP, OH, CEO, PROJECT_MANAGER
    }
}
