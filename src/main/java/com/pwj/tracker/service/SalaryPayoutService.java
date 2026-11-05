package com.pwj.tracker.service;

import com.pwj.tracker.account.service.FundMovementService;
import com.pwj.tracker.dto.SalaryDto;
import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.model.Employee;
import com.pwj.tracker.model.SalaryApproval;
import com.pwj.tracker.model.SalaryPayoutLine;
import com.pwj.tracker.repository.AppUserRepository;
import com.pwj.tracker.repository.EmployeeRepository;
import com.pwj.tracker.repository.SalaryApprovalRepository;
import com.pwj.tracker.repository.SalaryPayoutLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Salary payment for a released month. When the CEO releases the month, {@link #createLines} freezes every employee's
 * net pay; the batch then waits in Accounts → Send for Payment for the VP, and the bank Excel can be downloaded only
 * once the VP approves. Beneficiary details (account number, IFSC) are read from the Employee Master.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalaryPayoutService {

    private static final List<AppUser.Role> VIEWERS = List.of(AppUser.Role.ADMIN, AppUser.Role.VP, AppUser.Role.OH, AppUser.Role.CEO);

    private final SalaryPayoutLineRepository lineRepo;
    private final SalaryApprovalRepository approvalRepo;
    private final SalaryService salaryService;
    private final AppUserRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final FundMovementService fundMovementService;

    /** Freezes the net pay of every employee on the released month's sheet and opens the batch for VP approval.
     *  Runs inside the CEO's release transaction, so a failure here rolls the release back instead of leaving a
     *  released month with nothing to pay. Idempotent. Employees with nothing to pay (net pay ≤ 0) are left out. */
    @Transactional
    public void createLines(int year, int month) {
        SalaryApproval a = approvalRepo.findByYearAndMonth(year, month)
                .orElseThrow(() -> new RuntimeException("Salary approval not found for " + month + "/" + year));
        if (lineRepo.existsByYearAndMonth(year, month)) return;
        for (SalaryDto.SheetRow r : salaryService.sheet(year, month)) {
            if (r.getTakeHome() == null || r.getTakeHome().signum() <= 0) continue;
            lineRepo.save(SalaryPayoutLine.builder()
                    .year(year).month(month).userId(r.getUserId())
                    .fullName(r.getName()).personCode(r.getEmployeeNumber())
                    .netPay(r.getTakeHome()).build());
        }
        a.setPayoutStatus("PENDING");
        approvalRepo.save(a);
    }

    /** Takes a month's payment batch off Accounts — used when the VP reopens (revises) the month. The lines are frozen
     *  net pay, so they are deleted and rebuilt from the corrected sheet at the next release. If the payment had
     *  already been approved, its Fund Management outflow is removed too (re-created when it is approved again).
     *  Returns the outflow amount that was removed, or null if there was none. */
    @Transactional
    public BigDecimal withdraw(int year, int month) {
        lineRepo.deleteAll(lineRepo.findByYearAndMonthOrderByFullNameAsc(year, month));
        approvalRepo.findByYearAndMonth(year, month).ifPresent(a -> {
            a.setPayoutStatus(null); a.setPayoutBy(null); a.setPayoutAt(null); a.setPayoutRemark(null);
            approvalRepo.save(a);
        });
        return fundMovementService.removeSalaryOutflow(year, month);
    }

    /** Every released month's payment batch, newest first, with each employee's bank details from the Employee Master. */
    public List<Map<String, Object>> list(String username) {
        requireViewer(username);
        List<SalaryApproval> batches = new ArrayList<>(approvalRepo.findByPayoutStatusIsNotNull());
        batches.sort(Comparator.comparing(SalaryApproval::getYear).thenComparing(SalaryApproval::getMonth).reversed());
        List<Map<String, Object>> out = new ArrayList<>();
        for (SalaryApproval a : batches) out.add(toView(a));
        return out;
    }

    /** VP approves the batch — this is what unlocks the Excel download. A rejected batch can still be approved later. */
    @Transactional
    public Map<String, Object> approve(int year, int month, String username) {
        AppUser actor = vp(username);
        SalaryApproval a = batch(year, month);
        if ("APPROVED".equals(a.getPayoutStatus())) throw new RuntimeException("This salary payment is already approved");
        a.setPayoutStatus("APPROVED");
        a.setPayoutBy(actor.getFullName()); a.setPayoutAt(LocalDateTime.now()); a.setPayoutRemark(null);
        Map<String, Object> view = toView(approvalRepo.save(a));
        // Fund Management: the approved salary goes out as an Actual Outflow (same transaction — fails together).
        int missing = (int) view.get("missingCount");
        BigDecimal total = (BigDecimal) view.get("total");
        BigDecimal outsideBankFile = total.subtract((BigDecimal) view.get("payable"));
        String note = "Salary for " + java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + " " + year
                + " — " + view.get("count") + " employees, approved by " + actor.getFullName() + " (VP)"
                + (missing > 0 ? ". Includes ₹" + outsideBankFile.toPlainString() + " for " + missing + " employee"
                        + (missing == 1 ? "" : "s") + " with no bank details, paid outside the bank file" : "");
        fundMovementService.recordSalaryOutflow(year, month, total, note);
        return view;
    }

    @Transactional
    public Map<String, Object> reject(int year, int month, String username, String remark) {
        AppUser actor = vp(username);
        if (remark == null || remark.isBlank()) throw new RuntimeException("Enter a reason for rejecting");
        SalaryApproval a = batch(year, month);
        if (!"PENDING".equals(a.getPayoutStatus())) throw new RuntimeException("Only a payment waiting for approval can be rejected");
        a.setPayoutStatus("REJECTED");
        a.setPayoutBy(actor.getFullName()); a.setPayoutAt(LocalDateTime.now()); a.setPayoutRemark(remark.trim());
        return toView(approvalRepo.save(a));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private Map<String, Object> toView(SalaryApproval a) {
        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO, payable = BigDecimal.ZERO;
        int missing = 0;
        for (SalaryPayoutLine l : lineRepo.findByYearAndMonthOrderByFullNameAsc(a.getYear(), a.getMonth())) {
            AppUser u = userRepo.findById(l.getUserId()).orElse(null);
            Employee e = u == null ? null : employeeRepo.findByLinkedUsername(u.getUsername()).orElse(null);
            String acct = e == null ? null : blankToNull(e.getBankAccountNumber());
            String ifsc = e == null ? null : blankToNull(e.getIfscCode());
            boolean bankMissing = acct == null || ifsc == null;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", l.getUserId());
            m.put("fullName", e != null && e.getFullName() != null ? e.getFullName() : l.getFullName());
            m.put("personCode", e != null && e.getPersonCode() != null ? e.getPersonCode() : l.getPersonCode());
            m.put("netPay", l.getNetPay());
            m.put("bankName", e == null ? null : blankToNull(e.getBankName()));
            m.put("accountNumber", acct);
            m.put("ifsc", ifsc);
            m.put("bankMissing", bankMissing);
            lines.add(m);
            total = total.add(l.getNetPay());
            if (bankMissing) missing++; else payable = payable.add(l.getNetPay());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("year", a.getYear()); m.put("month", a.getMonth());
        m.put("status", a.getPayoutStatus());
        m.put("releasedAt", a.getReleasedAt());
        m.put("decidedBy", a.getPayoutBy()); m.put("decidedAt", a.getPayoutAt()); m.put("remark", a.getPayoutRemark());
        m.put("count", lines.size()); m.put("missingCount", missing);
        m.put("total", total); m.put("payable", payable);
        m.put("lines", lines);
        return m;
    }

    private SalaryApproval batch(int year, int month) {
        SalaryApproval a = approvalRepo.findByYearAndMonth(year, month)
                .orElseThrow(() -> new RuntimeException("No salary payment for that month"));
        if (a.getPayoutStatus() == null) throw new RuntimeException("No salary payment for that month");
        return a;
    }

    private void requireViewer(String username) {
        AppUser u = username == null ? null : userRepo.findByUsername(username).orElse(null);
        if (u == null || !VIEWERS.contains(u.getRole())) throw new RuntimeException("You can't view salary payments");
    }

    private AppUser vp(String username) {
        AppUser u = userRepo.findByUsernameAndActiveTrue(username == null ? "" : username)
                .orElseThrow(() -> new RuntimeException("Not signed in"));
        if (u.getRole() != AppUser.Role.VP) throw new RuntimeException("Only the VP can approve a salary payment");
        return u;
    }

    private static String blankToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
