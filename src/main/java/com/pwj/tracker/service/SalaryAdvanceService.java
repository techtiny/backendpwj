package com.pwj.tracker.service;

import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.model.SalaryAdvance;
import com.pwj.tracker.repository.AppUserRepository;
import com.pwj.tracker.repository.EmployeeSalaryRepository;
import com.pwj.tracker.repository.SalaryAdvanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Salary advances: raise → OH/VP approve (term adjustable) → Admin pays → instalments recovered through the Salary sheet. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalaryAdvanceService {

    public static final int MAX_TERM_MONTHS = 12;

    private final SalaryAdvanceRepository repo;
    private final AppUserRepository userRepo;
    private final EmployeeSalaryRepository salaryRepo;
    private final SalaryApprovalService approvalService;

    // ── Requests ─────────────────────────────────────────────────────────

    @Transactional
    public SalaryAdvance raise(String username, BigDecimal amount, String reason, Integer termMonths) {
        AppUser user = userRepo.findByUsernameAndActiveTrue(username == null ? "" : username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (amount == null || amount.signum() <= 0) throw new RuntimeException("Enter the advance amount");
        int term = termMonths == null ? 0 : termMonths;
        if (term < 1 || term > MAX_TERM_MONTHS) throw new RuntimeException("Repayment term must be 1 to " + MAX_TERM_MONTHS + " months");
        if (salaryRepo.findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user.getId(), LocalDate.now()).isEmpty())
            throw new RuntimeException("No salary is defined for you yet — ask HR, an advance is recovered from salary");
        if (repo.existsByUsernameAndStatusIn(user.getUsername(), List.of("PENDING", "APPROVED")))
            throw new RuntimeException("You already have an advance request in progress");
        return repo.save(SalaryAdvance.builder()
                .username(user.getUsername()).fullName(user.getFullName())
                .raisedByRole(user.getRole() != null ? user.getRole().name() : null)
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .reason(reason == null || reason.isBlank() ? null : reason.trim())
                .requestedTermMonths(term).termMonths(term)
                .status("PENDING").build());
    }

    @Transactional
    public SalaryAdvance cancel(Long id, String username) {
        SalaryAdvance a = get(id);
        if (!a.getUsername().equalsIgnoreCase(username == null ? "" : username)) throw new RuntimeException("Not your request");
        if (!"PENDING".equals(a.getStatus())) throw new RuntimeException("Only a pending request can be cancelled");
        a.setStatus("CANCELLED");
        return repo.save(a);
    }

    /** OH / VP approves; they may change the repayment term. */
    @Transactional
    public SalaryAdvance approve(Long id, String username, String comment, Integer termMonths) {
        AppUser actor = actor(username);
        if (actor.getRole() != AppUser.Role.OH && actor.getRole() != AppUser.Role.VP)
            throw new RuntimeException("Only OH or VP can approve an advance");
        SalaryAdvance a = get(id);
        if (!"PENDING".equals(a.getStatus())) throw new RuntimeException("Request already processed");
        if (a.getUsername().equalsIgnoreCase(actor.getUsername())) throw new RuntimeException("You can't approve your own request");
        if (termMonths != null) {
            if (termMonths < 1 || termMonths > MAX_TERM_MONTHS) throw new RuntimeException("Repayment term must be 1 to " + MAX_TERM_MONTHS + " months");
            a.setTermMonths(termMonths);
        }
        a.setStatus("APPROVED");
        a.setApprovedBy(actor.getFullName()); a.setApprovedByRole(actor.getRole().name());
        a.setApprovalComment(comment == null || comment.isBlank() ? null : comment.trim());
        a.setApprovedAt(LocalDateTime.now());
        return repo.save(a);
    }

    @Transactional
    public SalaryAdvance reject(Long id, String username, String comment) {
        AppUser actor = actor(username);
        if (actor.getRole() != AppUser.Role.OH && actor.getRole() != AppUser.Role.VP)
            throw new RuntimeException("Only OH or VP can reject an advance");
        if (comment == null || comment.isBlank()) throw new RuntimeException("Enter a reason for rejecting");
        SalaryAdvance a = get(id);
        if (!"PENDING".equals(a.getStatus())) throw new RuntimeException("Request already processed");
        a.setStatus("REJECTED");
        a.setApprovedBy(actor.getFullName()); a.setApprovedByRole(actor.getRole().name());
        a.setApprovalComment(comment.trim()); a.setApprovedAt(LocalDateTime.now());
        return repo.save(a);
    }

    /** Which payroll an advance raised on this date belongs to. Salary for month M covers the 26th of M-1 to the 25th
     *  of M and is still being finalised until about the 7th of M+1 — so an advance raised on or before the 7th goes
     *  on the previous month's payroll, one raised from the 8th to the 25th on this month's, and one raised after
     *  the 25th on next month's. */
    static LocalDate payrollMonthFor(LocalDate raised) {
        LocalDate first = raised.withDayOfMonth(1);
        if (raised.getDayOfMonth() <= 7) return first.minusMonths(1);
        if (raised.getDayOfMonth() <= 25) return first;
        return first.plusMonths(1);
    }

    public Map<String, Object> startOptions() { return startOptions(null); }

    /** Payroll months an advance can start recovering in: the previous, the current and the next cycle — released
     *  months are listed but locked. The default follows the 7th-of-the-month rule above, using the date the advance
     *  was raised, moved forward to the first month still open if that one is already released. */
    public Map<String, Object> startOptions(Long advanceId) {
        LocalDate today = LocalDate.now();
        LocalDate raised = advanceId == null ? today
                : repo.findById(advanceId).map(a -> a.getCreatedAt() != null ? a.getCreatedAt().toLocalDate() : today).orElse(today);
        LocalDate ruleMonth = payrollMonthFor(raised);
        // Payroll month M covers the 26th of the previous month to the 25th of M.
        LocalDate cur = today.getDayOfMonth() > 25 ? today.plusMonths(1).withDayOfMonth(1) : today.withDayOfMonth(1);
        List<Map<String, Object>> options = new ArrayList<>();
        Map<String, Object> def = null;
        for (int d = -1; d <= 1; d++) {
            LocalDate m = cur.plusMonths(d);
            boolean released = approvalService.isReleased(m.getYear(), m.getMonthValue());
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("year", m.getYear()); o.put("month", m.getMonthValue()); o.put("released", released);
            options.add(o);
            if (def == null && !released && !m.isBefore(ruleMonth)) def = o;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("options", options);
        out.put("default", def);
        return out;
    }

    /** One-time catch-up for advances paid before the 7th-of-the-month rule: move recovery to the payroll the rule gives,
     *  but only if nothing has been recovered yet and that month is still open. Each advance is processed once
     *  (flagged afterwards), so a month Admin picks later is never overridden. Returns how many were moved. */
    @Transactional
    public int applyStartRuleToLegacyAdvances() {
        int moved = 0;
        for (SalaryAdvance a : repo.findAll()) {
            if (!"PAID".equals(a.getStatus()) || a.getStartRuleApplied() != null || a.getStartYear() == null) continue;
            a.setStartRuleApplied(true);
            LocalDate rule = payrollMonthFor(a.getCreatedAt() != null ? a.getCreatedAt().toLocalDate() : LocalDate.now());
            LocalDate cur = LocalDate.of(a.getStartYear(), a.getStartMonth(), 1);
            boolean untouched = true;
            LocalDate m = cur;
            for (int i = 0; i < a.getTermMonths(); i++) {
                if (approvalService.isReleased(m.getYear(), m.getMonthValue())) { untouched = false; break; }
                m = m.plusMonths(1);
            }
            if (rule.isBefore(cur) && untouched && !approvalService.isReleased(rule.getYear(), rule.getMonthValue())) {
                approvalService.resetIfInProgress(cur.getYear(), cur.getMonthValue());
                a.setStartYear(rule.getYear()); a.setStartMonth(rule.getMonthValue());
                approvalService.resetIfInProgress(rule.getYear(), rule.getMonthValue());
                moved++;
            }
            repo.save(a);
        }
        return moved;
    }

    private void checkStart(int year, int month) {
        boolean ok = false;
        for (Map<String, Object> o : (List<Map<String, Object>>) startOptions().get("options")) {
            if ((int) o.get("year") == year && (int) o.get("month") == month) { ok = !(boolean) o.get("released"); }
        }
        if (!ok) throw new RuntimeException("Pick a payroll month that isn't released yet (previous, current or next month)");
    }

    /** Admin has transferred the money: repayment starts in the payroll month Admin picks (default: the earliest one
     *  not yet released). */
    @Transactional
    public SalaryAdvance markPaid(Long id, String username, Integer startYear, Integer startMonth) {
        AppUser actor = actor(username);
        if (actor.getRole() != AppUser.Role.ADMIN) throw new RuntimeException("Only Admin can mark an advance as paid");
        SalaryAdvance a = get(id);
        if (!"APPROVED".equals(a.getStatus())) throw new RuntimeException("Only an approved advance can be marked paid");
        int year, month;
        if (startYear != null && startMonth != null) { year = startYear; month = startMonth; }
        else {
            Map<String, Object> d = (Map<String, Object>) startOptions(id).get("default");
            if (d == null) throw new RuntimeException("Every nearby payroll month is released — nothing to deduct from yet");
            year = (int) d.get("year"); month = (int) d.get("month");
        }
        checkStart(year, month);
        a.setStatus("PAID");
        a.setPaidBy(actor.getFullName()); a.setPaidAt(LocalDateTime.now());
        a.setStartYear(year); a.setStartMonth(month); a.setStartRuleApplied(true);
        approvalService.resetIfInProgress(year, month); // that month's sheet just changed — approvals restart at Admin
        return repo.save(a);
    }

    /** Admin moves where recovery starts — only while no instalment has been recovered (none of its months released). */
    @Transactional
    public SalaryAdvance changeStart(Long id, String username, int year, int month) {
        AppUser actor = actor(username);
        if (actor.getRole() != AppUser.Role.ADMIN) throw new RuntimeException("Only Admin can change the start month");
        SalaryAdvance a = get(id);
        if (!"PAID".equals(a.getStatus())) throw new RuntimeException("Only a paid advance has a start month");
        int y = a.getStartYear(), mo = a.getStartMonth();
        for (int i = 0; i < a.getTermMonths(); i++) {
            if (approvalService.isReleased(y, mo)) throw new RuntimeException("An instalment has already been recovered in a released month — the start can't move");
            LocalDate n = LocalDate.of(y, mo, 1).plusMonths(1); y = n.getYear(); mo = n.getMonthValue();
        }
        checkStart(year, month);
        approvalService.resetIfInProgress(a.getStartYear(), a.getStartMonth()); // old start month's sheet changes
        a.setStartYear(year); a.setStartMonth(month); a.setStartRuleApplied(true);
        approvalService.resetIfInProgress(year, month);
        return repo.save(a);
    }

    // ── Reads ────────────────────────────────────────────────────────────

    public List<Map<String, Object>> mine(String username) {
        return enrich(repo.findByUsernameOrderByCreatedAtDesc(username));
    }

    public List<Map<String, Object>> pending() {
        return enrich(repo.findByStatusOrderByCreatedAtDesc("PENDING"));
    }

    public List<Map<String, Object>> all() {
        return enrich(repo.findAllByOrderByCreatedAtDesc());
    }

    // ── Salary-sheet hook ────────────────────────────────────────────────

    /** Total advance instalments to recover from this employee's salary for the payroll month. */
    public BigDecimal deductionFor(String username, int year, int month) {
        BigDecimal total = BigDecimal.ZERO;
        for (SalaryAdvance a : repo.findByUsernameAndStatus(username, "PAID")) {
            total = total.add(instalmentFor(a, year, month));
        }
        return total;
    }

    /** Equal instalments, with the last one absorbing the rounding so the total is exactly the advance. */
    private BigDecimal instalmentFor(SalaryAdvance a, int year, int month) {
        if (a.getStartYear() == null || a.getStartMonth() == null) return BigDecimal.ZERO;
        int idx = (year - a.getStartYear()) * 12 + (month - a.getStartMonth());
        int term = a.getTermMonths();
        if (idx < 0 || idx >= term) return BigDecimal.ZERO;
        BigDecimal each = a.getAmount().divide(BigDecimal.valueOf(term), 2, RoundingMode.DOWN);
        return idx == term - 1 ? a.getAmount().subtract(each.multiply(BigDecimal.valueOf(term - 1))) : each;
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private List<Map<String, Object>> enrich(List<SalaryAdvance> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SalaryAdvance a : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId()); m.put("username", a.getUsername()); m.put("fullName", a.getFullName());
            m.put("amount", a.getAmount()); m.put("reason", a.getReason());
            m.put("requestedTermMonths", a.getRequestedTermMonths()); m.put("termMonths", a.getTermMonths());
            m.put("status", a.getStatus());
            m.put("approvedBy", a.getApprovedBy()); m.put("approvedByRole", a.getApprovedByRole());
            m.put("approvalComment", a.getApprovalComment()); m.put("approvedAt", a.getApprovedAt());
            m.put("paidBy", a.getPaidBy()); m.put("paidAt", a.getPaidAt());
            m.put("createdAt", a.getCreatedAt());
            m.put("monthlyInstalment", a.getAmount().divide(BigDecimal.valueOf(a.getTermMonths()), 2, RoundingMode.DOWN));
            // Repayment schedule (only once paid): one row per instalment; "recovered" once that month's salary is released.
            List<Map<String, Object>> schedule = new ArrayList<>();
            BigDecimal recovered = BigDecimal.ZERO;
            if ("PAID".equals(a.getStatus()) && a.getStartYear() != null) {
                int y = a.getStartYear(), mo = a.getStartMonth();
                for (int i = 0; i < a.getTermMonths(); i++) {
                    BigDecimal amt = instalmentFor(a, y, mo);
                    boolean done = approvalService.isReleased(y, mo);
                    if (done) recovered = recovered.add(amt);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("year", y); row.put("month", mo); row.put("amount", amt); row.put("recovered", done);
                    schedule.add(row);
                    LocalDate n = LocalDate.of(y, mo, 1).plusMonths(1); y = n.getYear(); mo = n.getMonthValue();
                }
            }
            m.put("startYear", a.getStartYear()); m.put("startMonth", a.getStartMonth());
            m.put("schedule", schedule);
            m.put("recovered", recovered);
            m.put("balance", "PAID".equals(a.getStatus()) ? a.getAmount().subtract(recovered) : a.getAmount());
            out.add(m);
        }
        return out;
    }

    private SalaryAdvance get(Long id) {
        return repo.findById(id).orElseThrow(() -> new RuntimeException("Advance request not found"));
    }

    private AppUser actor(String username) {
        if (username == null || username.isBlank()) throw new RuntimeException("Not signed in");
        return userRepo.findByUsernameAndActiveTrue(username).orElseThrow(() -> new RuntimeException("Unknown user: " + username));
    }
}
