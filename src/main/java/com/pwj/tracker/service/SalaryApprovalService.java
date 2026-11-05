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

import java.math.BigDecimal;
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
    // Resolved lazily — the payout service needs the salary sheet, which in turn needs this service.
    private final ObjectProvider<SalaryPayoutService> payoutService;

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
        } else if (role == AppUser.Role.OH) {
            if (Boolean.TRUE.equals(a.getOhApproved())) throw new RuntimeException("OH has already approved");
            a.setOhApproved(true); a.setOhBy(actor.getFullName()); a.setOhAt(now);
        } else if (role == AppUser.Role.VP) {
            if (Boolean.TRUE.equals(a.getVpApproved())) throw new RuntimeException("VP has already approved");
            a.setVpApproved(true); a.setVpBy(actor.getFullName()); a.setVpAt(now);
        } else if (role == AppUser.Role.CEO) {
            if (!Boolean.TRUE.equals(a.getOhApproved()) || !Boolean.TRUE.equals(a.getVpApproved()))
                throw new RuntimeException("The CEO gives the final approval after both OH and VP have approved");
            a.setCeoApproved(true); a.setCeoBy(actor.getFullName()); a.setCeoAt(now);
            a.setReleased(true); a.setReleasedAt(now);
        } else {
            throw new RuntimeException("Admin has already approved — waiting for OH, VP and then the CEO");
        }
        a.setRejectedBy(null); a.setRejectedRole(null); a.setRejectRemark(null); a.setRejectedAt(null);
        SalaryApproval saved = repo.save(a);
        if (Boolean.TRUE.equals(saved.getReleased())) {
            // Freeze everyone's net pay and open the payment for VP approval — in this same transaction, so a
            // failure rolls the release back rather than leaving a released month with nothing to pay.
            payoutService.getObject().createLines(year, month);
            notifyEmployees(year, month);
        }
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
        boolean admin = Boolean.TRUE.equals(a.getAdminApproved());
        boolean oh = Boolean.TRUE.equals(a.getOhApproved()), vp = Boolean.TRUE.equals(a.getVpApproved());
        // Anyone whose turn it is may send the sheet back: OH / VP while their own approval is pending, the CEO at the end.
        boolean allowed = !admin ? role == AppUser.Role.ADMIN
                : role == AppUser.Role.OH ? !oh
                : role == AppUser.Role.VP ? !vp
                : role == AppUser.Role.CEO && oh && vp;
        if (!allowed) throw new RuntimeException("It is not your turn to review this month's salary");
        clear(a);
        a.setRejectedBy(actor.getFullName()); a.setRejectedRole(role.name());
        a.setRejectRemark(remark.trim()); a.setRejectedAt(LocalDateTime.now());
        return toView(repo.save(a), year, month);
    }

    /** VP reopens a month — before or after the CEO released it. Everything goes back to Admin: approvals are cleared,
     *  the month is un-released (payslips locked again, sheet editable by Admin), and its salary payment batch is
     *  withdrawn from Accounts. Allowed even after the VP approved the payment — then the Fund Management outflow for
     *  the month is removed as well (it is re-created when the corrected salary is approved again). Revising does not
     *  undo a bank transfer that was already made. */
    @Transactional
    public Map<String, Object> revise(int year, int month, String username, String remark) {
        AppUser actor = actor(username);
        if (actor.getRole() != AppUser.Role.VP) throw new RuntimeException("Only the VP can revise the salary");
        if (remark == null || remark.isBlank()) throw new RuntimeException("Enter what needs to be revised");
        SalaryApproval a = repo.findByYearAndMonth(year, month).orElse(null);
        if (a == null || (!Boolean.TRUE.equals(a.getAdminApproved()) && !Boolean.TRUE.equals(a.getReleased())))
            throw new RuntimeException("Nothing to revise yet — the sheet hasn't been approved");
        boolean wasApproved = "APPROVED".equals(a.getPayoutStatus());
        BigDecimal removed = payoutService.getObject().withdraw(year, month);
        if (wasApproved || removed != null)
            log.info("Salary {}/{} revised by {} after payment approval — removed Fund Management outflow {}", month, year, actor.getFullName(), removed);
        clear(a);
        a.setReleased(false); a.setReleasedAt(null);
        a.setRejectedBy(actor.getFullName()); a.setRejectedRole(actor.getRole().name());
        a.setRejectRemark(remark.trim()); a.setRejectedAt(LocalDateTime.now());
        return toView(repo.save(a), year, month);
    }

    // ── Used by SalaryService ───────────────────────────────────────────

    /** True when this month has been released by the CEO (sheet is locked). */
    public boolean isReleased(int year, int month) {
        return repo.findByYearAndMonth(year, month).map(a -> Boolean.TRUE.equals(a.getReleased())).orElse(false);
    }

    /** The sheet is in review: Admin has approved it and the chain hasn't been released or sent back. Nobody can edit
     *  it then — editing opens again (for Admin only) when OH / VP / CEO rejects it. */
    public boolean isLockedForReview(int year, int month) {
        return repo.findByYearAndMonth(year, month)
                .map(a -> Boolean.TRUE.equals(a.getAdminApproved()) && !Boolean.TRUE.equals(a.getReleased())).orElse(false);
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
        a.setOhApproved(false); a.setOhBy(null); a.setOhAt(null);
        a.setVpApproved(false); a.setVpBy(null); a.setVpAt(null);
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
        boolean ceo = a != null && Boolean.TRUE.equals(a.getCeoApproved());
        boolean released = a != null && Boolean.TRUE.equals(a.getReleased());
        boolean legacy = a != null && Boolean.TRUE.equals(a.getReviewApproved());   // old single OH-or-VP approval
        // A month released under the old single approval counts as both OH and VP having signed off.
        boolean oh = a != null && (Boolean.TRUE.equals(a.getOhApproved()) || (legacy && ("OH".equals(a.getReviewRole()) || released)));
        boolean vp = a != null && (Boolean.TRUE.equals(a.getVpApproved()) || (legacy && ("VP".equals(a.getReviewRole()) || released)));
        m.put("year", year); m.put("month", month);
        m.put("adminApproved", admin); m.put("adminBy", a != null ? a.getAdminBy() : null); m.put("adminAt", a != null ? a.getAdminAt() : null);
        m.put("ohApproved", oh);
        m.put("ohBy", a != null ? (a.getOhBy() != null ? a.getOhBy() : (oh ? a.getReviewBy() : null)) : null);
        m.put("ohAt", a != null ? (a.getOhAt() != null ? a.getOhAt() : (oh ? a.getReviewAt() : null)) : null);
        m.put("vpApproved", vp);
        m.put("vpBy", a != null ? (a.getVpBy() != null ? a.getVpBy() : (vp ? a.getReviewBy() : null)) : null);
        m.put("vpAt", a != null ? (a.getVpAt() != null ? a.getVpAt() : (vp ? a.getReviewAt() : null)) : null);
        m.put("ceoApproved", ceo); m.put("ceoBy", a != null ? a.getCeoBy() : null); m.put("ceoAt", a != null ? a.getCeoAt() : null);
        m.put("released", a != null && Boolean.TRUE.equals(a.getReleased()));
        m.put("payoutStatus", a != null ? a.getPayoutStatus() : null);   // PENDING / APPROVED / REJECTED once released
        m.put("releasedAt", a != null ? a.getReleasedAt() : null);
        // Who acts next: ADMIN → REVIEW (OH/VP) → CEO → none
        m.put("nextStep", ceo ? "NONE" : (oh && vp) ? "CEO" : admin ? "OH_VP" : "ADMIN");
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
