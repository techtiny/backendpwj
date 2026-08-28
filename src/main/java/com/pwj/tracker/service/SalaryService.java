package com.pwj.tracker.service;

import com.pwj.tracker.dto.SalaryDto;
import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.model.EmployeeSalary;
import com.pwj.tracker.model.LeaveRequest;
import com.pwj.tracker.model.SalaryMonthAdjustment;
import com.pwj.tracker.repository.AppUserRepository;
import com.pwj.tracker.repository.EmployeeSalaryRepository;
import com.pwj.tracker.repository.LeaveRequestRepository;
import com.pwj.tracker.repository.SalaryMonthAdjustmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalaryService {

    // Payroll runs on a fixed 30-day month; one casual leave a month is free.
    private static final int DAYS_IN_MONTH = 30;
    private static final int FREE_CL_PER_MONTH = 1;
    private static final BigDecimal PF_AMOUNT = new BigDecimal("1800");
    private static final BigDecimal PT_AMOUNT = new BigDecimal("208");
    private static final BigDecimal BASIC_PCT = new BigDecimal("0.50");
    private static final BigDecimal HRA_PCT   = new BigDecimal("0.125");
    private static final BigDecimal OTHER_PCT = new BigDecimal("0.375");

    private final AppUserRepository userRepo;
    private final EmployeeSalaryRepository salaryRepo;
    private final SalaryMonthAdjustmentRepository adjustmentRepo;
    private final LeaveRequestRepository leaveRepo;

    // ── Salary structures ────────────────────────────────────────────────

    /** Every active employee, with their current structure or a flag that none is set. */
    public List<SalaryDto.StructureView> listStructures() {
        LocalDate today = LocalDate.now();
        return userRepo.findAllByActiveTrue().stream()
                .filter(u -> u.getRole() != AppUser.Role.CEO)
                .sorted(Comparator.comparing(u -> Optional.ofNullable(u.getEmployeeNumber()).orElse("")))
                .map(u -> {
                    EmployeeSalary s = salaryRepo
                            .findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(u.getId(), today)
                            .orElse(null);
                    SalaryDto.StructureView.StructureViewBuilder b = SalaryDto.StructureView.builder()
                            .userId(u.getId())
                            .employeeNumber(u.getEmployeeNumber())
                            .name(u.getFullName())
                            .designation(u.getDesignation() != null ? u.getDesignation() : String.valueOf(u.getRole()))
                            .role(String.valueOf(u.getRole()))
                            .hasSalary(s != null);
                    if (s != null) {
                        BigDecimal g = s.getMonthlyGross();
                        b.monthlyGross(g)
                         .basic(pct(g, BASIC_PCT)).hra(pct(g, HRA_PCT)).otherAllowance(pct(g, OTHER_PCT))
                         .pfApplicable(s.getPfApplicable()).ptApplicable(s.getPtApplicable())
                         .effectiveFrom(s.getEffectiveFrom()).note(s.getNote());
                    }
                    return b.build();
                })
                .collect(Collectors.toList());
    }

    public List<EmployeeSalary> structureHistory(Long userId) {
        return salaryRepo.findByUserIdOrderByEffectiveFromDesc(userId);
    }

    /** Define a first structure, or add a revision (appraisal) — always a new effective-dated row. */
    @Transactional
    public EmployeeSalary saveStructure(SalaryDto.StructureRequest req) {
        AppUser user = userRepo.findById(req.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (req.getMonthlyGross() == null || req.getMonthlyGross().signum() <= 0) {
            throw new RuntimeException("Monthly gross must be greater than zero");
        }
        EmployeeSalary s = EmployeeSalary.builder()
                .userId(user.getId())
                .employeeNumber(user.getEmployeeNumber())
                .monthlyGross(req.getMonthlyGross().setScale(2, RoundingMode.HALF_UP))
                .pfApplicable(req.getPfApplicable() == null || req.getPfApplicable())
                .ptApplicable(req.getPtApplicable() == null || req.getPtApplicable())
                .effectiveFrom(req.getEffectiveFrom() != null ? req.getEffectiveFrom() : LocalDate.now().withDayOfMonth(1))
                .note(req.getNote())
                .createdBy(req.getActionBy())
                .build();
        return salaryRepo.save(s);
    }

    // ── Monthly salary sheet ─────────────────────────────────────────────

    public List<SalaryDto.SheetRow> sheet(int year, int month) {
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());

        Map<Long, SalaryMonthAdjustment> adj = adjustmentRepo.findByYearAndMonth(year, month).stream()
                .collect(Collectors.toMap(SalaryMonthAdjustment::getUserId, a -> a));

        List<SalaryDto.SheetRow> rows = new ArrayList<>();
        for (AppUser u : userRepo.findAllByActiveTrue()) {
            if (u.getRole() == AppUser.Role.CEO) continue;
            EmployeeSalary s = salaryRepo
                    .findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(u.getId(), monthEnd)
                    .orElse(null);
            if (s == null) continue; // no structure yet — nothing to pay
            rows.add(compute(u, s, year, month, monthStart, monthEnd, adj.get(u.getId())));
        }
        rows.sort(Comparator.comparing(r -> Optional.ofNullable(r.getEmployeeNumber()).orElse("")));
        return rows;
    }

    @Transactional
    public SalaryDto.SheetRow saveAdjustment(Long userId, int year, int month, SalaryDto.AdjustmentRequest req) {
        AppUser user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        SalaryMonthAdjustment a = adjustmentRepo.findByUserIdAndYearAndMonth(userId, year, month)
                .orElseGet(() -> SalaryMonthAdjustment.builder().userId(userId).year(year).month(month).finalized(false).build());
        a.setExtraWorkingDays(req.getExtraWorkingDays());
        a.setManualLopDays(req.getManualLopDays());
        a.setManualWorkingDays(req.getManualWorkingDays());
        a.setRemarks(req.getRemarks());
        if (req.getFinalized() != null) a.setFinalized(req.getFinalized());
        a.setUpdatedBy(req.getActionBy());
        adjustmentRepo.save(a);

        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        EmployeeSalary s = salaryRepo
                .findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(userId, monthEnd)
                .orElseThrow(() -> new RuntimeException("Define a salary structure for this employee first"));
        return compute(user, s, year, month, monthStart, monthEnd, a);
    }

    // ── Core computation ─────────────────────────────────────────────────

    private SalaryDto.SheetRow compute(AppUser u, EmployeeSalary s, int year, int month,
                                       LocalDate monthStart, LocalDate monthEnd, SalaryMonthAdjustment a) {
        BigDecimal leaveDays = approvedLeaveDaysInMonth(u.getUsername(), monthStart, monthEnd);
        BigDecimal computedLop = leaveDays.subtract(BigDecimal.valueOf(FREE_CL_PER_MONTH)).max(BigDecimal.ZERO);

        BigDecimal extra = a != null && a.getExtraWorkingDays() != null ? a.getExtraWorkingDays() : BigDecimal.ZERO;
        BigDecimal lop = a != null && a.getManualLopDays() != null ? a.getManualLopDays() : computedLop;

        BigDecimal workingDays;
        if (a != null && a.getManualWorkingDays() != null) {
            workingDays = a.getManualWorkingDays();
        } else {
            workingDays = BigDecimal.valueOf(DAYS_IN_MONTH).subtract(lop).add(extra);
        }
        workingDays = workingDays.max(BigDecimal.ZERO).min(BigDecimal.valueOf(DAYS_IN_MONTH));

        BigDecimal factor = workingDays.divide(BigDecimal.valueOf(DAYS_IN_MONTH), 10, RoundingMode.HALF_UP);

        BigDecimal fGross = s.getMonthlyGross();
        boolean pf = Boolean.TRUE.equals(s.getPfApplicable());
        boolean pt = Boolean.TRUE.equals(s.getPtApplicable());
        BigDecimal fPf = pf ? PF_AMOUNT : BigDecimal.ZERO;
        BigDecimal fPt = pt ? PT_AMOUNT : BigDecimal.ZERO;
        BigDecimal fTotalDed = fPf.add(fPt);
        BigDecimal fEmployer = pf ? PF_AMOUNT : BigDecimal.ZERO;

        BigDecimal aGross = money(fGross.multiply(factor));
        BigDecimal aPf = money(fPf.multiply(factor));      // PF prorates with attendance
        BigDecimal aPt = fPt;                              // PT is a flat monthly figure
        BigDecimal aTotalDed = aPf.add(aPt);
        BigDecimal aEmployer = aPf;

        String remarks = a != null && a.getRemarks() != null && !a.getRemarks().isBlank()
                ? a.getRemarks() : autoRemark(leaveDays, lop);

        return SalaryDto.SheetRow.builder()
                .userId(u.getId()).employeeNumber(u.getEmployeeNumber()).name(u.getFullName())
                .designation(u.getDesignation() != null ? u.getDesignation() : String.valueOf(u.getRole()))
                .daysInMonth(DAYS_IN_MONTH).leaveDays(strip(leaveDays)).freeCasualLeave(FREE_CL_PER_MONTH)
                .lopDays(strip(lop)).extraWorkingDays(strip(extra)).workingDays(strip(workingDays))
                .fixedGross(money(fGross)).fixedBasic(pct(fGross, BASIC_PCT)).fixedHra(pct(fGross, HRA_PCT))
                .fixedOther(pct(fGross, OTHER_PCT)).fixedTotalGross(money(fGross))
                .fixedPf(money(fPf)).fixedPt(money(fPt)).fixedTotalDed(money(fTotalDed))
                .fixedTakeHome(money(fGross.subtract(fTotalDed))).fixedEmployer(money(fEmployer))
                .fixedCtc(money(fGross.add(fEmployer)))
                .gross(aGross).basic(pct(aGross, BASIC_PCT)).hra(pct(aGross, HRA_PCT))
                .otherAllowance(pct(aGross, OTHER_PCT)).totalGross(aGross)
                .pf(aPf).pt(aPt).totalDed(aTotalDed).takeHome(money(aGross.subtract(aTotalDed)))
                .employer(aEmployer).ctc(money(aGross.add(aEmployer)))
                .remarks(remarks)
                .finalized(a != null && Boolean.TRUE.equals(a.getFinalized()))
                .build();
    }

    /** Approved leave days that fall inside the month; PERMISSION counts as hours/8. */
    private BigDecimal approvedLeaveDaysInMonth(String username, LocalDate monthStart, LocalDate monthEnd) {
        BigDecimal total = BigDecimal.ZERO;
        for (LeaveRequest lr : leaveRepo.findByUsernameOrderByCreatedAtDesc(username)) {
            if (!"APPROVED".equalsIgnoreCase(lr.getStatus())) continue;
            if ("COMP_OFF".equalsIgnoreCase(lr.getLeaveType())) continue; // earned by extra work — no LOP
            if ("PERMISSION".equalsIgnoreCase(lr.getLeaveType())) {
                if (lr.getFromDate() == null || lr.getFromDate().isBefore(monthStart) || lr.getFromDate().isAfter(monthEnd)) continue;
                int hrs = lr.getPermissionHours() != null ? lr.getPermissionHours() : 0;
                total = total.add(BigDecimal.valueOf(hrs).divide(BigDecimal.valueOf(8), 2, RoundingMode.HALF_UP));
                continue;
            }
            LocalDate from = lr.getFromDate(), to = lr.getToDate() != null ? lr.getToDate() : lr.getFromDate();
            if (from == null) continue;
            LocalDate s = from.isBefore(monthStart) ? monthStart : from;
            LocalDate e = to.isAfter(monthEnd) ? monthEnd : to;
            if (e.isBefore(s)) continue;
            long days = java.time.temporal.ChronoUnit.DAYS.between(s, e) + 1;
            total = total.add(BigDecimal.valueOf(days));
        }
        return total;
    }

    private String autoRemark(BigDecimal leaveDays, BigDecimal lop) {
        if (leaveDays.signum() == 0) return "No leave";
        String ld = strip(leaveDays).toPlainString();
        if (lop.signum() == 0) return ld + " day leave — within 1 CL";
        return ld + " day leave — 1 CL free, " + strip(lop).toPlainString() + " LOP";
    }

    private BigDecimal pct(BigDecimal base, BigDecimal p) { return money(base.multiply(p)); }
    private BigDecimal money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal strip(BigDecimal v) { return v.stripTrailingZeros().scale() < 0 ? v.setScale(0) : v.stripTrailingZeros(); }
}
