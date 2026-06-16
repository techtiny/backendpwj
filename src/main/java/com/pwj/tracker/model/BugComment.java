package com.pwj.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "bug_comments", indexes = {
    @Index(name = "idx_bug_comment_bug_id", columnList = "bug_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BugComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bug_id", nullable = false)
    private Long bugId;

    // COMMENT, STATUS_CHANGE, SEVERITY_CHANGE, ASSIGNED, UPDATED
    @Column(nullable = false, length = 20)
    private String type;

    @Column(name = "comment_text", nullable = false, length = 2000)
    private String commentText;

    @Column(name = "author_username", length = 100)
    private String authorUsername;

    @Column(name = "author_name", length = 150)
    private String authorName;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
