package com.pwj.tracker.service;

import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.model.Resignation;
import com.pwj.tracker.repository.AppUserRepository;
import com.pwj.tracker.repository.ResignationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Resignation workflow: VP / Admin enables it for an employee → the employee applies with a 2-month notice period
 * (last working day = resignation date + 2 months) → VP / Admin can change it, accept it or mark it withdrawn.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResignationService {

    public static final int NOTICE_MONTHS = 2;
    private static final Set<AppUser.Role> MANAGERS = EnumSet.of(AppUser.Role.ADMIN, AppUser.Role.VP);
    private static final Set<AppUser.Role> VIEWERS  = EnumSet.of(AppUser.Role.ADMIN, AppUser.Role.VP, AppUser.Role.OH, AppUser.Role.CEO);
    /** A resignation in one of these states is the employee's current one. */
    private static final List<String> OPEN = List.of("ENABLED", "SUBMITTED", "ACCEPTED");

    private final ResignationRepository repo;
    private final AppUserRepository userRepo;

    /** The employee's current resignation (enabled / submitted / accepted), or their latest closed one, or null. */
    public Resignation mine(String username) {
        return repo.findFirstByUsernameIgnoreCaseAndStatusInOrderByCreatedAtDesc(username, OPEN)
                .orElseGet(() -> repo.findFirstByUsernameIgnoreCaseOrderByCreatedAtDesc(username).orElse(null));
    }

    public List<Resignation> all(String actor) {
        AppUser a = user(actor);
        if (!VIEWERS.contains(a.getRole())) throw new RuntimeException("Not allowed");
        return repo.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public Resignation enable(String actor, String employeeUsername) {
        AppUser a = manager(actor);
        AppUser e = user(employeeUsername);
        if (e.getUsername().equalsIgnoreCase(a.getUsername())) throw new RuntimeException("You can't enable your own resignation — another VP or Admin must");
        if (repo.findFirstByUsernameIgnoreCaseAndStatusInOrderByCreatedAtDesc(e.getUsername(), OPEN).isPresent())
            throw new RuntimeException(e.getFullName() + " already has an open resignation");
        return repo.save(Resignation.builder()
                .username(e.getUsername()).fullName(e.getFullName()).employeeRole(e.getRole() == null ? null : e.getRole().name())
                .status("ENABLED").enabledBy(a.getFullName()).enabledAt(LocalDateTime.now())
                .noticeMonths(NOTICE_MONTHS).build());
    }

    /** Employee applies: resignation date today, last working day after the 2-month notice period. */
    @Transactional
    public Resignation apply(String username, String reason) {
        AppUser u = user(username);
        Resignation r = repo.findFirstByUsernameIgnoreCaseAndStatusInOrderByCreatedAtDesc(u.getUsername(), List.of("ENABLED"))
                .orElseThrow(() -> new RuntimeException("Resignation isn't enabled for you. Please contact your VP or Admin."));
        if (reason == null || reason.isBlank()) throw new RuntimeException("Enter the reason for resigning");
        LocalDate today = LocalDate.now();
        r.setResignationDate(today);
        r.setLastWorkingDay(today.plusMonths(NOTICE_MONTHS));
        r.setNoticeMonths(NOTICE_MONTHS);
        r.setReason(reason.trim());
        r.setSubmittedAt(LocalDateTime.now());
        r.setStatus("SUBMITTED");
        return repo.save(r);
    }

    /**
     * VP / Admin changes a resignation. Any field left null is unchanged. Changing only the resignation date moves the
     * last working day with it (still 2 months' notice); an explicit last working day overrides that.
     * status may be SUBMITTED (re-open), ACCEPTED or WITHDRAWN.
     */
    @Transactional
    public Resignation update(Long id, String actor, LocalDate resignationDate, LocalDate lastWorkingDay,
                              String reason, String remarks, String status) {
        AppUser a = manager(actor);
        Resignation r = repo.findById(id).orElseThrow(() -> new RuntimeException("Resignation not found"));
        if (r.getUsername().equalsIgnoreCase(a.getUsername())) throw new RuntimeException("You can't change your own resignation — another VP or Admin must");
        if ("ENABLED".equals(r.getStatus()) || "DISABLED".equals(r.getStatus()))
            throw new RuntimeException("The employee hasn't applied yet");
        if (resignationDate != null) {
            r.setResignationDate(resignationDate);
            if (lastWorkingDay == null) r.setLastWorkingDay(resignationDate.plusMonths(NOTICE_MONTHS));
        }
        if (lastWorkingDay != null) {
            if (r.getResignationDate() != null && lastWorkingDay.isBefore(r.getResignationDate()))
                throw new RuntimeException("Last working day can't be before the resignation date");
            r.setLastWorkingDay(lastWorkingDay);
        }
        if (reason != null && !reason.isBlank()) r.setReason(reason.trim());
        if (remarks != null) r.setRemarks(remarks.trim());
        if (status != null && !status.isBlank() && !status.equals(r.getStatus())) {
            if (!List.of("SUBMITTED", "ACCEPTED", "WITHDRAWN").contains(status)) throw new RuntimeException("Invalid status");
            r.setStatus(status);
            r.setDecidedBy(a.getFullName());
            r.setDecidedAt(LocalDateTime.now());
        }
        r.setUpdatedBy(a.getFullName());
        return repo.save(r);
    }

    /** Turn off an enabled resignation the employee hasn't applied yet. */
    @Transactional
    public Resignation disable(Long id, String actor) {
        AppUser a = manager(actor);
        Resignation r = repo.findById(id).orElseThrow(() -> new RuntimeException("Resignation not found"));
        if (!"ENABLED".equals(r.getStatus())) throw new RuntimeException("Only a resignation the employee hasn't applied yet can be disabled");
        r.setStatus("DISABLED");
        r.setUpdatedBy(a.getFullName());
        return repo.save(r);
    }

    public long submittedCount(String excludeUsername) {
        return repo.findByStatus("SUBMITTED").stream().filter(r -> !r.getUsername().equalsIgnoreCase(excludeUsername)).count();
    }

    private AppUser user(String username) {
        return userRepo.findByUsernameAndActiveTrue(username == null ? "" : username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    private AppUser manager(String username) {
        AppUser a = user(username);
        if (!MANAGERS.contains(a.getRole())) throw new RuntimeException("Only a VP or Admin can do this");
        return a;
    }
}
