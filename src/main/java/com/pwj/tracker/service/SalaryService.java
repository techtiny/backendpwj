package com.pwj.tracker.service;

import com.pwj.tracker.dto.SalaryDto;
import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.model.Attendance;
import com.pwj.tracker.model.EmployeeSalary;
import com.pwj.tracker.model.Holiday;
import com.pwj.tracker.model.LeaveRequest;
import com.pwj.tracker.model.SalaryMonthAdjustment;
import com.pwj.tracker.repository.AppUserRepository;
import com.pwj.tracker.repository.AttendanceRepository;
import com.pwj.tracker.repository.EmployeeSalaryRepository;
import com.pwj.tracker.repository.LeaveRequestRepository;
import com.pwj.tracker.repository.SalaryMonthAdjustmentRepository;
import com.pwj.tracker.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalaryService {

    // Payroll cycle: 26th of the previous month through the 25th of the selected month —
    // not a calendar month (e.g. month=September means the Aug 26 – Sep 25 cycle). The day
    // count varies by cycle (28-31 days depending on the months it spans) and is computed
    // per-cycle in cycleBounds(). One casual leave is free.
    private static final int FREE_CL_PER_MONTH = 1;
    /** Marks a leave request HR recorded on an employee's behalf — Attendance shows those days as "Adjusted". */
    public static final String REGULARIZED_COMMENT = "Regularized by HR: leave taken but not applied in the portal";
    // Each unauthorized no-show/incomplete-checkout day costs 1.5 LOP days, not 1 — a stricter
    // penalty than leave that was actually applied for.
    private static final BigDecimal UNAUTHORIZED_LOP_MULTIPLIER = new BigDecimal("1.5");
    // Roles Team Attendance requires a daily check-in from — only these are eligible for the
    // Sunday/holiday auto Extra Working Day credit below, and subject to the unauthorized-
    // absence LOP rule.
    private static final Set<AppUser.Role> CHECKIN_ROLES = AppUser.CHECKIN_ROLES;
    // Default PF is 12% of Basic (Basic = 50% of gross); default PT is a flat 208. Either can be
    // overridden per structure when defining / revising a salary.
    private static final BigDecimal PF_PCT_OF_BASIC = new BigDecimal("0.12");
    private static final BigDecimal PT_AMOUNT = new BigDecimal("208");
    private static final BigDecimal BASIC_PCT = new BigDecimal("0.50");
    private static final BigDecimal HRA_PCT   = new BigDecimal("0.125");
    private static final BigDecimal OTHER_PCT = new BigDecimal("0.375");

    private final AppUserRepository userRepo;
    private final EmployeeSalaryRepository salaryRepo;
    private final SalaryMonthAdjustmentRepository adjustmentRepo;
    private final LeaveRequestRepository leaveRepo;
    private final HolidayRepository holidayRepo;
    private final AttendanceRepository attendanceRepo;
    private final SalaryApprovalService approvalService;
    private final SalaryAdvanceService advanceService;

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
                         .pfAmount(pfOf(s)).ptAmount(ptOf(s))
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
        LocalDate effective = req.getEffectiveFrom() != null ? req.getEffectiveFrom() : LocalDate.now().withDayOfMonth(1);
        // A released month is locked: a revision effective inside it would silently change its figures.
        var locked = approvalService.releasedMonthsAffectedBy(effective);
        if (!locked.isEmpty()) {
            var l = locked.get(0);
            throw new RuntimeException("Salary for " + java.time.Month.of(l.getMonth()).getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
                    + " " + l.getYear() + " is already released — set the effective date after its payroll cycle (26th of that month onwards)");
        }
        approvalService.resetAllInProgress(); // the sheets change — approvals restart at Admin
        EmployeeSalary s = EmployeeSalary.builder()
                .userId(user.getId())
                .employeeNumber(user.getEmployeeNumber())
                .monthlyGross(req.getMonthlyGross().setScale(2, RoundingMode.HALF_UP))
                .pfApplicable(req.getPfApplicable() == null || req.getPfApplicable())
                .ptApplicable(req.getPtApplicable() == null || req.getPtApplicable())
                .pfAmount(req.getPfAmount() != null ? money(req.getPfAmount().max(BigDecimal.ZERO)) : null)
                .ptAmount(req.getPtAmount() != null ? money(req.getPtAmount().max(BigDecimal.ZERO)) : null)
                .effectiveFrom(req.getEffectiveFrom() != null ? req.getEffectiveFrom() : LocalDate.now().withDayOfMonth(1))
                .note(req.getNote())
                .createdBy(req.getActionBy())
                .build();
        return salaryRepo.save(s);
    }

    // ── Monthly salary sheet ─────────────────────────────────────────────

    /** The payroll cycle for (year, month): the 26th of the previous month through the 25th of this one. */
    private LocalDate[] cycleBounds(int year, int month) {
        LocalDate end = LocalDate.of(year, month, 25);
        LocalDate start = end.minusMonths(1).withDayOfMonth(26);
        return new LocalDate[]{start, end};
    }

    public List<SalaryDto.SheetRow> sheet(int year, int month) {
        LocalDate[] bounds = cycleBounds(year, month);
        LocalDate monthStart = bounds[0], monthEnd = bounds[1];

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
        if (approvalService.isReleased(year, month)) throw new RuntimeException("This month's salary is released and locked");
        approvalService.resetIfInProgress(year, month); // any change restarts the approval chain at Admin
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

        LocalDate[] bounds = cycleBounds(year, month);
        LocalDate monthStart = bounds[0], monthEnd = bounds[1];
        EmployeeSalary s = salaryRepo
                .findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(userId, monthEnd)
                .orElseThrow(() -> new RuntimeException("Define a salary structure for this employee first"));
        return compute(user, s, year, month, monthStart, monthEnd, a);
    }

    /** The day was flagged as an unauthorized absence only because the employee never applied for leave in the
     *  portal. HR (Admin / VP) records it as an approved leave on their behalf — it then counts as ordinary leave
     *  (the free CL, then LOP at 1x) instead of an unauthorized absence at 1.5x with no free CL. */
    @Transactional
    public SalaryDto.SheetRow regularizeAbsence(Long userId, int year, int month, SalaryDto.RegularizeRequest req) {
        if (approvalService.isReleased(year, month)) throw new RuntimeException("This month's salary is released and locked");
        AppUser actor = userRepo.findByUsernameAndActiveTrue(req.getUsername() == null ? "" : req.getUsername())
                .orElseThrow(() -> new RuntimeException("Not signed in"));
        if (actor.getRole() != AppUser.Role.ADMIN && actor.getRole() != AppUser.Role.VP)
            throw new RuntimeException("Only Admin or VP can record leave on an employee's behalf");
        AppUser user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        if (req.getDate() == null) throw new RuntimeException("Pick the date");
        String type = req.getLeaveType() == null || req.getLeaveType().isBlank() ? "CASUAL" : req.getLeaveType().trim().toUpperCase();
        if (!List.of("CASUAL", "SICK", "EARNED", "OTHER").contains(type)) throw new RuntimeException("Leave type must be Casual, Sick, Earned or Other");

        LocalDate[] bounds = cycleBounds(year, month);
        Set<LocalDate> holidays = holidayRepo.findByDateBetweenOrderByDateAsc(bounds[0], bounds[1])
                .stream().map(Holiday::getDate).collect(Collectors.toSet());
        if (!unauthorizedAbsenceDates(user, bounds[0], bounds[1], holidays).contains(req.getDate()))
            throw new RuntimeException("That day isn't an unauthorized absence in this month's cycle");

        leaveRepo.save(LeaveRequest.builder()
                .username(user.getUsername()).fullName(user.getFullName())
                .leaveType(type).fromDate(req.getDate()).toDate(req.getDate())
                .totalDays(BigDecimal.ONE)
                .reason((req.getReason() == null || req.getReason().isBlank() ? "Leave not applied in the portal" : req.getReason().trim())
                        + " — recorded by " + actor.getFullName())
                .status("APPROVED").approvedBy(actor.getFullName())
                .approvalComment(REGULARIZED_COMMENT)
                .approvedAt(java.time.LocalDateTime.now())
                .build());
        approvalService.resetIfInProgress(year, month);

        EmployeeSalary s = salaryRepo
                .findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(userId, bounds[1])
                .orElseThrow(() -> new RuntimeException("Define a salary structure for this employee first"));
        SalaryMonthAdjustment a = adjustmentRepo.findByUserIdAndYearAndMonth(userId, year, month).orElse(null);
        return compute(user, s, year, month, bounds[0], bounds[1], a);
    }

    // ── Core computation ─────────────────────────────────────────────────

    private SalaryDto.SheetRow compute(AppUser u, EmployeeSalary s, int year, int month,
                                       LocalDate monthStart, LocalDate monthEnd, SalaryMonthAdjustment a) {
        int daysInMonth = (int) (java.time.temporal.ChronoUnit.DAYS.between(monthStart, monthEnd) + 1);

        Set<LocalDate> holidays = holidayRepo.findByDateBetweenOrderByDateAsc(monthStart, monthEnd)
                .stream().map(Holiday::getDate).collect(Collectors.toSet());
        BigDecimal leaveDays = approvedLeaveDaysInMonth(u.getUsername(), monthStart, monthEnd, holidays);
        List<LocalDate> unauthorizedDates = unauthorizedAbsenceDates(u, monthStart, monthEnd, holidays);
        BigDecimal unauthorizedDays = BigDecimal.valueOf(unauthorizedDates.size());
        // Unauthorized no-shows skip the free-CL grace entirely, and cost 1.5 LOP days each —
        // stricter than leave that was actually applied for.
        BigDecimal computedLop = leaveDays.subtract(BigDecimal.valueOf(FREE_CL_PER_MONTH)).max(BigDecimal.ZERO)
                .add(unauthorizedDays.multiply(UNAUTHORIZED_LOP_MULTIPLIER));

        BigDecimal autoExtra = autoExtraWorkingDays(u, monthStart, monthEnd, holidays);
        BigDecimal extra = a != null && a.getExtraWorkingDays() != null ? a.getExtraWorkingDays() : autoExtra;
        BigDecimal lop = a != null && a.getManualLopDays() != null ? a.getManualLopDays() : computedLop;

        BigDecimal workingDays;
        if (a != null && a.getManualWorkingDays() != null) {
            workingDays = a.getManualWorkingDays();
        } else {
            workingDays = BigDecimal.valueOf(daysInMonth).subtract(lop).add(extra);
        }
        workingDays = workingDays.max(BigDecimal.ZERO).min(BigDecimal.valueOf(daysInMonth));

        BigDecimal factor = workingDays.divide(BigDecimal.valueOf(daysInMonth), 10, RoundingMode.HALF_UP);

        BigDecimal fGross = s.getMonthlyGross();
        BigDecimal fPf = pfOf(s);
        BigDecimal fPt = ptOf(s);
        BigDecimal fTotalDed = fPf.add(fPt);
        BigDecimal fEmployer = fPf;

        BigDecimal aGross = money(fGross.multiply(factor));
        BigDecimal aPf = money(fPf.multiply(factor));      // PF prorates with attendance
        BigDecimal aPt = fPt;                              // PT is a flat monthly figure
        BigDecimal aAdvance = money(advanceService.deductionFor(u.getUsername(), year, month)); // flat monthly instalment — not prorated
        BigDecimal aTotalDed = aPf.add(aPt).add(aAdvance);
        BigDecimal aEmployer = aPf;

        String remarks = a != null && a.getRemarks() != null && !a.getRemarks().isBlank()
                ? a.getRemarks() : autoRemark(leaveDays, lop, unauthorizedDays);

        return SalaryDto.SheetRow.builder()
                .userId(u.getId()).employeeNumber(u.getEmployeeNumber()).name(u.getFullName())
                .designation(u.getDesignation() != null ? u.getDesignation() : String.valueOf(u.getRole()))
                .cycleStart(monthStart).cycleEnd(monthEnd)
                .daysInMonth(daysInMonth).leaveDays(strip(leaveDays)).freeCasualLeave(FREE_CL_PER_MONTH)
                .unauthorizedDays(strip(unauthorizedDays)).unauthorizedDates(unauthorizedDates)
                .lopDays(strip(lop)).extraWorkingDays(strip(extra)).workingDays(strip(workingDays))
                .fixedGross(money(fGross)).fixedBasic(pct(fGross, BASIC_PCT)).fixedHra(pct(fGross, HRA_PCT))
                .fixedOther(pct(fGross, OTHER_PCT)).fixedTotalGross(money(fGross))
                .fixedPf(money(fPf)).fixedPt(money(fPt)).fixedTotalDed(money(fTotalDed))
                .fixedTakeHome(money(fGross.subtract(fTotalDed))).fixedEmployer(money(fEmployer))
                .fixedCtc(money(fGross.add(fEmployer)))
                .gross(aGross).basic(pct(aGross, BASIC_PCT)).hra(pct(aGross, HRA_PCT))
                .otherAllowance(pct(aGross, OTHER_PCT)).totalGross(aGross)
                .pf(aPf).pt(aPt).advanceDeduction(aAdvance).totalDed(aTotalDed).takeHome(money(aGross.subtract(aTotalDed)))
                .employer(aEmployer).ctc(money(aGross.add(aEmployer)))
                .remarks(remarks)
                .finalized(a != null && Boolean.TRUE.equals(a.getFinalized()))
                .build();
    }

    /** Approved leave days that fall inside the month; PERMISSION is excluded from LOP entirely,
     *  and any day that's a declared company holiday doesn't count either — the office was
     *  already closed, so it shouldn't burn the employee's leave/CL or push them into LOP. */
    private BigDecimal approvedLeaveDaysInMonth(String username, LocalDate monthStart, LocalDate monthEnd, Set<LocalDate> holidays) {
        BigDecimal total = BigDecimal.ZERO;
        for (LeaveRequest lr : leaveRepo.findByUsernameOrderByCreatedAtDesc(username)) {
            if (!"APPROVED".equalsIgnoreCase(lr.getStatus())) continue;
            if ("COMP_OFF".equalsIgnoreCase(lr.getLeaveType())) continue; // earned by extra work — no LOP
            if ("PERMISSION".equalsIgnoreCase(lr.getLeaveType())) continue; // hours-based, doesn't count toward LOP
            if ("HALF_DAY".equalsIgnoreCase(lr.getLeaveType())) {
                if (lr.getFromDate() == null || lr.getFromDate().isBefore(monthStart) || lr.getFromDate().isAfter(monthEnd)) continue;
                if (holidays.contains(lr.getFromDate())) continue;
                total = total.add(new BigDecimal("0.5"));
                continue;
            }
            LocalDate from = lr.getFromDate(), to = lr.getToDate() != null ? lr.getToDate() : lr.getFromDate();
            if (from == null) continue;
            LocalDate s = from.isBefore(monthStart) ? monthStart : from;
            LocalDate e = to.isAfter(monthEnd) ? monthEnd : to;
            if (e.isBefore(s)) continue;
            long days = java.time.temporal.ChronoUnit.DAYS.between(s, e) + 1;
            long holidayDays = holidays.stream().filter(h -> !h.isBefore(s) && !h.isAfter(e)).count();
            total = total.add(BigDecimal.valueOf(Math.max(0, days - holidayDays)));
        }
        return total;
    }

    /** Mon-Sat, no *complete* attendance (missing check-in, or checked in but never checked
     *  out — same "Missing Check-Out" days Attendance's Needs Review tab flags as ABSENT), no
     *  approved leave, not a declared holiday, and only up to yesterday (never today — it's
     *  still in progress). Team Attendance roles only. Each such day costs 1.5 LOP days
     *  (UNAUTHORIZED_LOP_MULTIPLIER) — the raw day count returned here is what's shown on the
     *  sheet, the multiplier is applied where it feeds into computedLop. */
    private List<LocalDate> unauthorizedAbsenceDates(AppUser u, LocalDate monthStart, LocalDate monthEnd, Set<LocalDate> holidays) {
        if (!CHECKIN_ROLES.contains(u.getRole())) return List.of();

        LocalDate cutoff = LocalDate.now().minusDays(1); // never judge today or the future
        LocalDate effectiveEnd = monthEnd.isAfter(cutoff) ? cutoff : monthEnd;
        if (effectiveEnd.isBefore(monthStart)) return List.of();

        Set<LocalDate> leaveCovered = leaveCoveredDates(u.getUsername(), monthStart, effectiveEnd);
        Set<LocalDate> completeAttendance = attendanceRepo.findByUsernameAndWorkDateBetween(u.getUsername(), monthStart, effectiveEnd)
                .stream().filter(att -> att.getCheckOutTime() != null)
                .map(Attendance::getWorkDate).collect(Collectors.toSet());

        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = monthStart; !d.isAfter(effectiveEnd); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            if (holidays.contains(d)) continue;
            if (leaveCovered.contains(d)) continue;
            if (completeAttendance.contains(d)) continue;
            days.add(d);
        }
        return days;
    }

    /** Sunday or a declared holiday, with a completed check-in + check-out that day — an
     *  employee working a day they weren't required to earns it back as an Extra Working Day,
     *  unless HR has entered a manual figure for the month (which always wins). */
    private BigDecimal autoExtraWorkingDays(AppUser u, LocalDate monthStart, LocalDate monthEnd, Set<LocalDate> holidays) {
        if (!CHECKIN_ROLES.contains(u.getRole())) return BigDecimal.ZERO;

        Map<LocalDate, Attendance> byDate = attendanceRepo.findByUsernameAndWorkDateBetween(u.getUsername(), monthStart, monthEnd)
                .stream().collect(Collectors.toMap(Attendance::getWorkDate, x -> x, (x, y) -> x));

        int count = 0;
        for (LocalDate d = monthStart; !d.isAfter(monthEnd); d = d.plusDays(1)) {
            boolean nonWorkingDay = d.getDayOfWeek() == DayOfWeek.SUNDAY || holidays.contains(d);
            if (!nonWorkingDay) continue;
            Attendance att = byDate.get(d);
            if (att != null && att.getCheckInTime() != null && att.getCheckOutTime() != null) count++;
        }
        return BigDecimal.valueOf(count);
    }

    /** Every date covered by an approved leave request (any full/half-day type — PERMISSION
     *  excluded, it's hours-based and doesn't explain a whole day with no check-in). */
    private Set<LocalDate> leaveCoveredDates(String username, LocalDate monthStart, LocalDate monthEnd) {
        Set<LocalDate> covered = new HashSet<>();
        for (LeaveRequest lr : leaveRepo.findByUsernameOrderByCreatedAtDesc(username)) {
            if (!"APPROVED".equalsIgnoreCase(lr.getStatus())) continue;
            if ("PERMISSION".equalsIgnoreCase(lr.getLeaveType())) continue;
            if ("HALF_DAY".equalsIgnoreCase(lr.getLeaveType())) {
                if (lr.getFromDate() != null) covered.add(lr.getFromDate());
                continue;
            }
            LocalDate from = lr.getFromDate(), to = lr.getToDate() != null ? lr.getToDate() : lr.getFromDate();
            if (from == null) continue;
            LocalDate s = from.isBefore(monthStart) ? monthStart : from;
            LocalDate e = to.isAfter(monthEnd) ? monthEnd : to;
            for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) covered.add(d);
        }
        return covered;
    }

    private String autoRemark(BigDecimal leaveDays, BigDecimal lop, BigDecimal unauthorizedDays) {
        String suffix = unauthorizedDays.signum() > 0
                ? " (incl. " + strip(unauthorizedDays).toPlainString() + " unauthorized absence × 1.5, no free CL)" : "";
        if (leaveDays.signum() == 0 && unauthorizedDays.signum() == 0) return "No leave";
        if (leaveDays.signum() == 0) return strip(lop).toPlainString() + " LOP" + suffix;
        String ld = strip(leaveDays).toPlainString();
        if (lop.signum() == 0) return ld + " day leave — within 1 CL";
        return ld + " day leave — 1 CL free, " + strip(lop).toPlainString() + " LOP" + suffix;
    }

    /** Monthly PF for a structure: 0 if not applicable, else the stored amount, else 12% of Basic. */
    private BigDecimal pfOf(EmployeeSalary s) {
        if (!Boolean.TRUE.equals(s.getPfApplicable())) return BigDecimal.ZERO;
        if (s.getPfAmount() != null) return money(s.getPfAmount());
        return money(pct(s.getMonthlyGross(), BASIC_PCT).multiply(PF_PCT_OF_BASIC));
    }

    /** Monthly PT for a structure: 0 if not applicable, else the stored amount, else 208. */
    private BigDecimal ptOf(EmployeeSalary s) {
        if (!Boolean.TRUE.equals(s.getPtApplicable())) return BigDecimal.ZERO;
        return s.getPtAmount() != null ? money(s.getPtAmount()) : PT_AMOUNT;
    }

    private BigDecimal pct(BigDecimal base, BigDecimal p) { return money(base.multiply(p)); }
    private BigDecimal money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal strip(BigDecimal v) { return v.stripTrailingZeros().scale() < 0 ? v.setScale(0) : v.stripTrailingZeros(); }
}
