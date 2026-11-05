package com.pwj.tracker.service;

import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.model.PerformanceRemark;
import com.pwj.tracker.model.PettyCash;
import com.pwj.tracker.model.SalaryApproval;
import com.pwj.tracker.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;

/**
 * What is waiting on this user in the HR module — shown on the Home page after login. Each item mirrors a screen the
 * user can actually act on, using the same role rules the screens use, so nobody is told about something they can't do.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HrNotificationService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Set<AppUser.Role> LEAVE_APPROVERS = EnumSet.of(AppUser.Role.VP, AppUser.Role.OH, AppUser.Role.ADMIN, AppUser.Role.CEO);
    private static final Set<AppUser.Role> PETTY_APPROVERS = EnumSet.of(AppUser.Role.ADMIN, AppUser.Role.CEO, AppUser.Role.VP, AppUser.Role.OH);
    private static final Set<AppUser.Role> REIMB_APPROVERS = EnumSet.of(AppUser.Role.ADMIN, AppUser.Role.VP);
    private static final Set<String> EXEC_ROLES = Set.of("VP", "OH", "CEO");

    private final AppUserRepository userRepo;
    private final LeaveRequestRepository leaveRepo;
    private final PettyCashRepository pettyRepo;
    private final SalaryAdvanceRepository advanceRepo;
    private final SalaryApprovalRepository salaryApprovalRepo;
    private final EmployeeRepository employeeRepo;
    private final PerformanceRemarkRepository remarkRepo;
    private final EmployeeService employeeService;

    public List<Map<String, Object>> forUser(String username) {
        AppUser u = userRepo.findByUsernameAndActiveTrue(username == null ? "" : username)
                .orElseThrow(() -> new RuntimeException("Not signed in"));
        AppUser.Role role = u.getRole();
        List<Map<String, Object>> out = new ArrayList<>();
        String me = u.getUsername();

        // Leave / permission requests
        if (LEAVE_APPROVERS.contains(role)) {
            long n = leaveRepo.findByStatusOrderByCreatedAtDesc("PENDING").stream()
                    .filter(l -> !me.equalsIgnoreCase(l.getUsername())).count();
            add(out, "leave", n, "leave / permission request", "awaiting your approval", "hr", "approvals");
        }

        // Petty cash and reimbursement
        if (PETTY_APPROVERS.contains(role)) {
            long n = pettyRepo.findByStatusAndRequestTypeOrderByCreatedAtDesc("PENDING", "PETTY_CASH").stream()
                    .filter(p -> !me.equalsIgnoreCase(p.getUsername())).count();
            add(out, "petty", n, "petty cash request", "awaiting your approval", "hr", "petty-cash");
        }
        if (REIMB_APPROVERS.contains(role)) {
            long n = pettyRepo.findByStatusAndRequestTypeOrderByCreatedAtDesc("PENDING", "REIMBURSEMENT").stream()
                    .filter(p -> !me.equalsIgnoreCase(p.getUsername()))
                    .filter(p -> p.getRaisedByRole() == null || !EXEC_ROLES.contains(p.getRaisedByRole().toUpperCase())) // exec claims need no sign-off
                    .count();
            add(out, "reimb", n, "reimbursement claim", "awaiting your approval", "hr", "reimbursement");
        }
        if (role == AppUser.Role.ADMIN) {
            long n = pettyRepo.findProofSubmittedEntries("PETTY_CASH").size() + pettyRepo.findProofSubmittedEntries("REIMBURSEMENT").size();
            add(out, "proof", n, "claim proof", "to verify", "hr", "petty-cash");
        }

        // Salary advances: OH / VP approve, Admin marks the approved ones paid
        if (role == AppUser.Role.OH || role == AppUser.Role.VP) {
            long n = advanceRepo.findByStatusOrderByCreatedAtDesc("PENDING").stream()
                    .filter(a -> !me.equalsIgnoreCase(a.getUsername())).count();
            add(out, "advance", n, "salary advance request", "awaiting your approval", "hr", "advance");
        }
        if (role == AppUser.Role.ADMIN) {
            long n = advanceRepo.findByStatusOrderByCreatedAtDesc("APPROVED").size();
            add(out, "advance-pay", n, "approved salary advance", "to mark as paid", "hr", "advance");
        }

        // Salary sheet approvals (Admin → OH and VP → CEO) for payroll months whose cycle has ended and aren't released yet
        LocalDate today = LocalDate.now(IST);
        for (int back = 0; back <= 2; back++) {
            LocalDate m = today.withDayOfMonth(1).minusMonths(back);
            LocalDate cycleEnd = m.withDayOfMonth(25);
            if (cycleEnd.isAfter(today) || cycleEnd.isBefore(today.minusDays(40))) continue;
            SalaryApproval a = salaryApprovalRepo.findByYearAndMonth(m.getYear(), m.getMonthValue()).orElse(null);
            if (a != null && Boolean.TRUE.equals(a.getReleased())) continue;
            boolean admin = a != null && Boolean.TRUE.equals(a.getAdminApproved());
            boolean oh = a != null && Boolean.TRUE.equals(a.getOhApproved());
            boolean vp = a != null && Boolean.TRUE.equals(a.getVpApproved());
            boolean mine = switch (role) {
                case ADMIN -> !admin;
                case OH -> admin && !oh;
                case VP -> admin && !vp;
                case CEO -> oh && vp;
                default -> false;
            };
            if (mine) {
                String label = Month.of(m.getMonthValue()).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.getYear();
                out.add(item("salary-" + label, 1, "Salary for " + label, role == AppUser.Role.ADMIN ? "ready for your approval" : "awaiting your approval", "hr", "salary"));
            }
        }
        // Salary payment waiting for the VP (Accounts → Team Payments)
        if (role == AppUser.Role.VP) {
            long n = salaryApprovalRepo.findByPayoutStatusIsNotNull().stream().filter(a -> "PENDING".equals(a.getPayoutStatus())).count();
            if (n > 0) out.add(item("salary-pay", n, n == 1 ? "1 salary payment" : n + " salary payments", "awaiting your approval in Accounts → Send for Payment → Team Payments", "account", null));
        }

        // Performance feedback: reviewers decide appeals and rate; employees accept or appeal their own comments
        if (EnumSet.of(AppUser.Role.ADMIN, AppUser.Role.VP, AppUser.Role.OH, AppUser.Role.CEO).contains(role)) {
            long appeals = 0, rate = 0;
            for (Map<String, Object> t : employeeService.pendingAppeals()) {
                String dept = t.get("department") == null ? "" : String.valueOf(t.get("department")).toLowerCase();
                if (dept.contains("management") && role != AppUser.Role.CEO) continue; // only the CEO reviews management
                if ("APPEAL".equals(t.get("type"))) appeals++; else rate++;
            }
            add(out, "appeals", appeals, "performance-comment appeal", "to decide", "hr", "employee-master");
            add(out, "rate", rate, "performance comment", "waiting for a rating", "hr", "employee-master");
        }
        employeeRepo.findByLinkedUsername(me).ifPresent(emp -> {
            LocalDateTime now = LocalDateTime.now();
            long n = remarkRepo.findByEmployeeIdOrderByRemarkDateDescIdDesc(emp.getId()).stream()
                    .filter(r -> r.getCreatedAt() != null && !r.getCreatedAt().isBefore(PerformanceRemark.REVIEW_FLOW_START))
                    .filter(r -> r.getAppealedAt() == null && r.getAcknowledgedAt() == null && r.getRating() == null)
                    .filter(r -> now.isBefore(r.getCreatedAt().plusHours(48))).count();
            add(out, "my-comments", n, "new performance comment", "for you to accept or appeal (within 48 hours)", "hr", "my-profile");
        });
        return out;
    }

    private void add(List<Map<String, Object>> out, String key, long count, String noun, String tail, String module, String tab) {
        if (count <= 0) return;
        out.add(item(key, count, count + " " + noun + (count == 1 ? "" : "s"), tail, module, tab));
    }

    private Map<String, Object> item(String key, long count, String title, String detail, String module, String tab) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key); m.put("count", count); m.put("title", title); m.put("detail", detail);
        m.put("module", module); m.put("tab", tab);
        return m;
    }
}
