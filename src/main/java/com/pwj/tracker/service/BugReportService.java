package com.pwj.tracker.service;

import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.model.BugComment;
import com.pwj.tracker.model.BugReport;
import com.pwj.tracker.repository.AppUserRepository;
import com.pwj.tracker.repository.BugCommentRepository;
import com.pwj.tracker.repository.BugReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BugReportService {

    private final BugReportRepository  repo;
    private final AppUserRepository    userRepo;
    private final BugCommentRepository commentRepo;

    @Transactional
    public BugReport create(String reportedBy, String title, String description,
                             String module, String severity, String attachmentUrl) {
        AppUser user = userRepo.findByUsernameAndActiveTrue(reportedBy)
                .orElseThrow(() -> new RuntimeException("User not found: " + reportedBy));

        BugReport bug = repo.save(BugReport.builder()
                .title(title)
                .description(description)
                .module(module != null ? module : "Other")
                .severity(severity != null ? severity : "Medium")
                .status("Open")
                .reportedBy(reportedBy)
                .reportedByName(user.getFullName())
                .attachmentUrl(attachmentUrl)
                .build());

        logComment(bug.getId(), "COMMENT", "Bug reported by " + user.getFullName(), reportedBy, user.getFullName());
        return bug;
    }

    public List<BugReport> getAll() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    public List<BugReport> getFiltered(String status, String severity, String module,
                                        String assignedTo, String search) {
        String likeSearch = (search == null || search.isBlank())
                ? null : "%" + search.trim().toLowerCase() + "%";
        return repo.findWithFilters(
                blankToNull(status), blankToNull(severity), blankToNull(module),
                blankToNull(assignedTo), likeSearch);
    }

    @Transactional
    public BugReport update(Long id, String title, String description,
                             String module, String severity, String actorUsername) {
        BugReport bug = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Bug not found"));
        if (title != null && !title.isBlank())       bug.setTitle(title.trim());
        if (description != null && !description.isBlank()) bug.setDescription(description.trim());
        if (module != null && !module.isBlank())     bug.setModule(module);
        if (severity != null && !severity.isBlank()) bug.setSeverity(severity);
        BugReport saved = repo.save(bug);
        String authorName = getFullName(actorUsername);
        logComment(id, "UPDATED", "Bug details updated by " + authorName, actorUsername, authorName);
        return saved;
    }

    @Transactional
    public BugReport updateStatus(Long id, String status, String actorUsername) {
        AppUser actor = userRepo.findByUsernameAndActiveTrue(actorUsername)
                .orElseThrow(() -> new RuntimeException("User not found: " + actorUsername));
        if (actor.getRole() != AppUser.Role.ADMIN && actor.getRole() != AppUser.Role.VP
                && actor.getRole() != AppUser.Role.OH && actor.getRole() != AppUser.Role.CEO) {
            throw new RuntimeException("Only Admin, VP, OH or CEO can update bug status");
        }
        BugReport bug = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Bug not found"));
        String oldStatus = bug.getStatus();
        bug.setStatus(status);
        BugReport saved = repo.save(bug);
        String authorName = actor.getFullName();
        logComment(id, "STATUS_CHANGE",
                "Status changed from " + oldStatus + " → " + status + " by " + authorName,
                actorUsername, authorName);
        return saved;
    }

    @Transactional
    public BugReport updateSeverity(Long id, String severity, String actorUsername) {
        BugReport bug = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Bug not found"));
        String oldSeverity = bug.getSeverity();
        bug.setSeverity(severity);
        BugReport saved = repo.save(bug);
        String authorName = getFullName(actorUsername);
        logComment(id, "SEVERITY_CHANGE",
                "Severity changed from " + oldSeverity + " → " + severity + " by " + authorName,
                actorUsername, authorName);
        return saved;
    }

    @Transactional
    public BugReport assign(Long id, String assignedTo, String actorUsername) {
        BugReport bug = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Bug not found"));
        String authorName = getFullName(actorUsername);

        if (assignedTo == null || assignedTo.isBlank()) {
            bug.setAssignedTo(null);
            bug.setAssignedToName(null);
            logComment(id, "ASSIGNED", "Unassigned by " + authorName, actorUsername, authorName);
        } else {
            // Assigned To is a free-text team label (e.g. "Happizo" / "Techtiny"), not an AppUser
            bug.setAssignedTo(assignedTo);
            bug.setAssignedToName(assignedTo);
            logComment(id, "ASSIGNED", "Assigned to " + assignedTo + " by " + authorName, actorUsername, authorName);
        }
        return repo.save(bug);
    }

    @Transactional
    public BugComment addComment(Long id, String text, String actorUsername) {
        repo.findById(id).orElseThrow(() -> new RuntimeException("Bug not found"));
        if (text == null || text.isBlank()) throw new RuntimeException("Comment cannot be empty");
        String authorName = getFullName(actorUsername);
        return commentRepo.save(BugComment.builder()
                .bugId(id)
                .type("COMMENT")
                .commentText(text.trim())
                .authorUsername(actorUsername)
                .authorName(authorName)
                .build());
    }

    public List<BugComment> getComments(Long id) {
        return commentRepo.findByBugIdOrderByCreatedAtAsc(id);
    }

    @Transactional
    public void delete(Long id) {
        BugReport bug = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Bug not found"));
        commentRepo.deleteAll(commentRepo.findByBugIdOrderByCreatedAtAsc(id));
        repo.delete(bug);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void logComment(Long bugId, String type, String text, String username, String authorName) {
        commentRepo.save(BugComment.builder()
                .bugId(bugId)
                .type(type)
                .commentText(text)
                .authorUsername(username)
                .authorName(authorName)
                .build());
    }

    private String getFullName(String username) {
        if (username == null || username.isBlank()) return "Unknown";
        return userRepo.findByUsernameAndActiveTrue(username)
                .map(AppUser::getFullName)
                .orElse(username);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
