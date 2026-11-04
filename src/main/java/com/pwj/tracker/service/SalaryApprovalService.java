package com.pwj.tracker.service;

import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.model.EmployeeSalary;
import com.pwj.tracker.model.SalaryApproval;
import com.pwj.tracker.repository.AppUserRepository;
import com.pwj.tracker.repository.EmployeeSalaryRepository;
import com.pwj.tracker.repository.SalaryApprovalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Release approval chain for a month's salary: Admin → OH/VP → CEO (final). See {@link SalaryApproval}. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalaryApprovalService {

    private final SalaryApprovalRepository repo;
    private final AppUserRepository userRepo;
    private final EmployeeSalaryRepository salaryRepo;
    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${salary.release.notify:true}")
    private boolean notifyOnRelease;
    @Value("${spring.mail.username:}")
    private String mailFrom;

    public Map<String, Object> view(int year, int month) {
        return toView(repo.findByYearAndMonth(year, month).orElse(null), year, month);
    }

    @Transactional
    public Map<String, Object> approve(int year, int month, String username) {
        AppUser actor = actor(username);
        SalaryApproval a = getOrCreate(year, month);
        if (Boolean.TRUE.equals(a.getReleased())) throw new RuntimeException("This month's salary is already released");
        LocalDateTime now = LocalDateTime.now();
        AppUser.Role role = actor.getRole();

        if (!Boolean.TRUE.equals(a.getAdminApproved())) {
            if (role != AppUser.Role.ADMIN) throw new RuntimeException("Admin must approve first");
            a.setAdminApproved(true); a.setAdminBy(actor.getFullName()); a.setAdminAt(now);
        } else if (!Boolean.TRUE.equals(a.getReviewApproved())) {
            if (role != AppUser.Role.OH && role != AppUser.Role.VP) throw new RuntimeException("OH or VP must approve next");
            a.setReviewApproved(true); a.setReviewBy(actor.getFullName()); a.setReviewRole(role.name()); a.setReviewAt(now);
        } else {
            if (role != AppUser.Role.CEO) throw new RuntimeException("The CEO gives the final approval");
            a.setCeoApproved(true); a.setCeoBy(actor.getFullName()); a.setCeoAt(now);
            a.setReleased(true); a.setReleasedAt(now);
        }
        a.setRejectedBy(null); a.setRejectedRole(null); a.setRejectRemark(null); a.setRejectedAt(null);
        SalaryApproval saved = repo.save(a);
        if (Boolean.TRUE.equals(saved.getReleased())) notifyEmployees(year, month);
        return toView(saved, year, month);
    }

    /** Rejecting at the current step sends the whole chain back to Admin, with the reason kept on the sheet. */
    @Transactional
    public Map<String, Object> reject(int year, int month, String username, String remark) {
        if (remark == null || remark.isBlank()) throw new RuntimeException("Enter a reason for rejecting");
        AppUser actor = actor(username);
        SalaryApproval a = getOrCreate(year, month);
        if (Boolean.TRUE.equals(a.getReleased())) throw new RuntimeException("This month's salary is already released");
        AppUser.Role role = actor.getRole();
        boolean allowed = !Boolean.TRUE.equals(a.getAdminApproved()) ? role == AppUser.Role.ADMIN
                : !Boolean.TRUE.equals(a.getReviewApproved()) ? (role == AppUser.Role.OH || role == AppUser.Role.VP)
                : role == AppUser.Role.CEO;
        if (!allowed) throw new RuntimeException("It is not your turn to review this month's salary");
        clear(a);
        a.setRejectedBy(actor.getFullName()); a.setRejectedRole(role.name());
        a.setRejectRemark(remark.trim()); a.setRejectedAt(LocalDateTime.now());
        return toView(repo.save(a), year, month);
    }

    // ── Used by SalaryService ───────────────────────────────────────────

    /** True when this month has been released by the CEO (sheet is locked). */
    public boolean isReleased(int year, int month) {
        return repo.findByYearAndMonth(year, month).map(a -> Boolean.TRUE.equals(a.getReleased())).orElse(false);
    }

    /** Any change to the sheet before release restarts the chain at Admin. */
    @Transactional
    public void resetIfInProgress(int year, int month) {
        repo.findByYearAndMonth(year, month).ifPresent(a -> {
            if (Boolean.TRUE.equals(a.getReleased())) return;
            if (Boolean.TRUE.equals(a.getAdminApproved())) { clear(a); repo.save(a); }
        });
    }

    @Transactional
    public void resetAllInProgress() {
        for (SalaryApproval a : repo.findByReleasedFalse()) {
            if (Boolean.TRUE.equals(a.getAdminApproved())) { clear(a); repo.save(a); }
        }
    }

    /** Released months whose payroll cycle ends on/after the date — a structure revision effective then would change them. */
    public List<SalaryApproval> releasedMonthsAffectedBy(LocalDate effectiveFrom) {
        List<SalaryApproval> out = new ArrayList<>();
        for (SalaryApproval a : repo.findByReleasedTrue()) {
            if (!LocalDate.of(a.getYear(), a.getMonth(), 25).isBefore(effectiveFrom)) out.add(a);
        }
        return out;
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private void clear(SalaryApproval a) {
        a.setAdminApproved(false); a.setAdminBy(null); a.setAdminAt(null);
        a.setReviewApproved(false); a.setReviewBy(null); a.setReviewRole(null); a.setReviewAt(null);
        a.setCeoApproved(false); a.setCeoBy(null); a.setCeoAt(null);
    }

    private SalaryApproval getOrCreate(int year, int month) {
        return repo.findByYearAndMonth(year, month)
                .orElseGet(() -> SalaryApproval.builder().year(year).month(month).build());
    }

    private AppUser actor(String username) {
        if (username == null || username.isBlank()) throw new RuntimeException("Not signed in");
        return userRepo.findByUsernameAndActiveTrue(username).orElseThrow(() -> new RuntimeException("Unknown user: " + username));
    }

    private Map<String, Object> toView(SalaryApproval a, int year, int month) {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean admin = a != null && Boolean.TRUE.equals(a.getAdminApproved());
        boolean review = a != null && Boolean.TRUE.equals(a.getReviewApproved());
        boolean ceo = a != null && Boolean.TRUE.equals(a.getCeoApproved());
        m.put("year", year); m.put("month", month);
        m.put("adminApproved", admin); m.put("adminBy", a != null ? a.getAdminBy() : null); m.put("adminAt", a != null ? a.getAdminAt() : null);
        m.put("reviewApproved", review); m.put("reviewBy", a != null ? a.getReviewBy() : null);
        m.put("reviewRole", a != null ? a.getReviewRole() : null); m.put("reviewAt", a != null ? a.getReviewAt() : null);
        m.put("ceoApproved", ceo); m.put("ceoBy", a != null ? a.getCeoBy() : null); m.put("ceoAt", a != null ? a.getCeoAt() : null);
        m.put("released", a != null && Boolean.TRUE.equals(a.getReleased()));
        m.put("releasedAt", a != null ? a.getReleasedAt() : null);
        // Who acts next: ADMIN → REVIEW (OH/VP) → CEO → none
        m.put("nextStep", ceo ? "NONE" : review ? "CEO" : admin ? "OH_VP" : "ADMIN");
        m.put("rejectedBy", a != null ? a.getRejectedBy() : null);
        m.put("rejectedRole", a != null ? a.getRejectedRole() : null);
        m.put("rejectRemark", a != null ? a.getRejectRemark() : null);
        m.put("rejectedAt", a != null ? a.getRejectedAt() : null);
        return m;
    }

    /** Tells every employee with a salary for the cycle that their payslip is available. Best effort, off the request thread. */
    private void notifyEmployees(int year, int month) {
        if (!notifyOnRelease) return;
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || mailFrom == null || mailFrom.isBlank() || mailFrom.startsWith("your-email")) {
            log.info("Salary release notification skipped — mail is not configured");
            return;
        }
        LocalDate cycleEnd = LocalDate.of(year, month, 25);
        List<String[]> recipients = new ArrayList<>(); // {email, name}
        for (AppUser u : userRepo.findAllByActiveTrue()) {
            if (u.getRole() == AppUser.Role.CEO || u.getEmail() == null || u.getEmail().isBlank()) continue;
            if (salaryRepo.findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(u.getId(), cycleEnd).isEmpty()) continue;
            recipients.add(new String[]{u.getEmail(), u.getFullName()});
        }
        String label = Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;
        CompletableFuture.runAsync(() -> {
            for (String[] r : recipients) {
                try {
                    SimpleMailMessage msg = new SimpleMailMessage();
                    msg.setFrom(mailFrom);
                    msg.setTo(r[0]);
                    msg.setSubject("Your salary payslip for " + label + " is available");
                    msg.setText("Hi " + r[1] + ",\n\nYour salary for " + label + " has been approved and released. "
                            + "You can view your payslip in the Happizo portal.\n\nHappizo Infrastructure and Solutions");
                    sender.send(msg);
                } catch (Exception e) {
                    log.warn("Could not send salary release mail to {}: {}", r[0], e.getMessage());
                }
            }
            log.info("Salary release notifications sent for {} to {} employees", label, recipients.size());
        });
    }
}
