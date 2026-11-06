package com.pwj.tracker.service;

import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.model.SalaryStatementRequest;
import com.pwj.tracker.repository.AppUserRepository;
import com.pwj.tracker.repository.SalaryStatementRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Salary statements are locked for everyone: request a month → Admin / VP approves → the employee can download it. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalaryStatementService {

    private static final Set<AppUser.Role> APPROVERS = EnumSet.of(AppUser.Role.ADMIN, AppUser.Role.VP);
    private static final Set<AppUser.Role> MONITORS  = EnumSet.of(AppUser.Role.ADMIN, AppUser.Role.VP, AppUser.Role.OH, AppUser.Role.CEO);

    private final SalaryStatementRequestRepository repo;
    private final AppUserRepository userRepo;

    @Transactional
    public SalaryStatementRequest request(String username, Integer year, Integer month) {
        AppUser u = actor(username);
        if (year == null || month == null || month < 1 || month > 12) throw new RuntimeException("Select the month and year");
        if (repo.findFirstByUsernameAndYearAndMonthAndStatusIn(u.getUsername(), year, month, List.of("PENDING", "APPROVED")).isPresent())
            throw new RuntimeException("You already have a request for this month");
        return repo.save(SalaryStatementRequest.builder()
                .username(u.getUsername()).fullName(u.getFullName()).year(year).month(month).status("PENDING").build());
    }

    public List<SalaryStatementRequest> mine(String username) {
        return repo.findByUsernameOrderByCreatedAtDesc(username);
    }

    public List<SalaryStatementRequest> pending(String username) {
        requireApprover(username);
        return repo.findByStatusOrderByCreatedAtDesc("PENDING");
    }

    /** Issue log for the HR dashboard — Admin / VP / OH / CEO. */
    public List<SalaryStatementRequest> all(String username) {
        AppUser a = actor(username);
        if (!MONITORS.contains(a.getRole())) throw new RuntimeException("Not allowed");
        return repo.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public SalaryStatementRequest decide(Long id, String username, boolean approve, String comment) {
        AppUser a = requireApprover(username);
        SalaryStatementRequest r = get(id);
        if (!"PENDING".equals(r.getStatus())) throw new RuntimeException("Request already processed");
        if (r.getUsername().equalsIgnoreCase(a.getUsername())) throw new RuntimeException("You can't approve your own request — another Admin or VP must");
        if (!approve && (comment == null || comment.isBlank())) throw new RuntimeException("Enter a reason for rejecting");
        r.setStatus(approve ? "APPROVED" : "REJECTED");
        r.setDecidedBy(a.getFullName()); r.setDecidedByRole(a.getRole().name()); r.setDecidedAt(LocalDateTime.now());
        r.setComment(comment == null || comment.isBlank() ? null : comment.trim());
        return repo.save(r);
    }

    /** Called when the employee downloads an approved statement — only the owner of an approved request may. */
    @Transactional
    public SalaryStatementRequest recordDownload(Long id, String username) {
        SalaryStatementRequest r = get(id);
        if (!r.getUsername().equalsIgnoreCase(username == null ? "" : username)) throw new RuntimeException("Not your request");
        if (!"APPROVED".equals(r.getStatus())) throw new RuntimeException("This statement isn't approved");
        r.setDownloadCount(r.getDownloadCount() + 1);
        r.setLastDownloadedAt(LocalDateTime.now());
        return repo.save(r);
    }

    public long pendingCount() { return repo.findByStatusOrderByCreatedAtDesc("PENDING").size(); }

    private SalaryStatementRequest get(Long id) {
        return repo.findById(id).orElseThrow(() -> new RuntimeException("Request not found"));
    }

    private AppUser actor(String username) {
        if (username == null || username.isBlank()) throw new RuntimeException("Not signed in");
        return userRepo.findByUsernameAndActiveTrue(username).orElseThrow(() -> new RuntimeException("Unknown user: " + username));
    }

    private AppUser requireApprover(String username) {
        AppUser a = actor(username);
        if (!APPROVERS.contains(a.getRole())) throw new RuntimeException("Only Admin or VP can approve a salary statement");
        return a;
    }
}
