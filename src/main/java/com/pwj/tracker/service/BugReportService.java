package com.pwj.tracker.service;

import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.model.BugReport;
import com.pwj.tracker.repository.AppUserRepository;
import com.pwj.tracker.repository.BugReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BugReportService {

    private final BugReportRepository repo;
    private final AppUserRepository   userRepo;

    @Transactional
    public BugReport create(String reportedBy, String title, String description,
                             String module, String severity, String attachmentUrl) {
        AppUser user = userRepo.findByUsernameAndActiveTrue(reportedBy)
                .orElseThrow(() -> new RuntimeException("User not found: " + reportedBy));

        return repo.save(BugReport.builder()
                .title(title)
                .description(description)
                .module(module != null ? module : "Other")
                .severity(severity != null ? severity : "Medium")
                .status("Open")
                .reportedBy(reportedBy)
                .reportedByName(user.getFullName())
                .attachmentUrl(attachmentUrl)
                .build());
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
    public BugReport updateStatus(Long id, String status) {
        BugReport bug = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Bug not found"));
        bug.setStatus(status);
        return repo.save(bug);
    }

    @Transactional
    public BugReport updateSeverity(Long id, String severity) {
        BugReport bug = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Bug not found"));
        bug.setSeverity(severity);
        return repo.save(bug);
    }

    @Transactional
    public BugReport assign(Long id, String assignedTo) {
        BugReport bug = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Bug not found"));

        if (assignedTo == null || assignedTo.isBlank()) {
            bug.setAssignedTo(null);
            bug.setAssignedToName(null);
        } else {
            AppUser user = userRepo.findByUsernameAndActiveTrue(assignedTo)
                    .orElseThrow(() -> new RuntimeException("User not found: " + assignedTo));
            bug.setAssignedTo(assignedTo);
            bug.setAssignedToName(user.getFullName());
        }
        return repo.save(bug);
    }

    @Transactional
    public void delete(Long id) {
        BugReport bug = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Bug not found"));
        repo.delete(bug);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
