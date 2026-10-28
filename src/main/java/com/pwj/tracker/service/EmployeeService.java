package com.pwj.tracker.service;

import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.model.Attendance;
import com.pwj.tracker.model.Employee;
import com.pwj.tracker.model.PerformanceRemark;
import com.pwj.tracker.repository.AppUserRepository;
import com.pwj.tracker.repository.AttendanceRepository;
import com.pwj.tracker.repository.EmployeeRepository;
import com.pwj.tracker.repository.PerformanceRemarkRepository;
import com.pwj.tracker.repository.PwjEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employeeRepo;
    private final AppUserRepository userRepo;
    private final AttendanceRepository attendanceRepo;
    private final PwjEntryRepository pwjEntryRepo;
    private final PerformanceRemarkRepository remarkRepo;

    public List<Employee> list() {
        return employeeRepo.findAllByOrderByFullNameAsc();
    }

    public Employee get(Long id) {
        return employeeRepo.findById(id).orElseThrow(() -> new RuntimeException("Employee not found: " + id));
    }

    @Transactional
    public Employee updatePhoto(Long id, String photoUrl) {
        Employee e = get(id);
        e.setPhotoUrl(photoUrl);
        return employeeRepo.save(e);
    }

    /** Manually link this master record to an app login — needed when the sheet's name didn't
     *  auto-match an AppUser exactly at import time. */
    @Transactional
    public Employee link(Long id, String username) {
        Employee e = get(id);
        if (username != null && !username.isBlank()) {
            userRepo.findByUsernameAndActiveTrue(username)
                    .orElseThrow(() -> new RuntimeException("No active app user: " + username));
        }
        e.setLinkedUsername(username == null || username.isBlank() ? null : username);
        return employeeRepo.save(e);
    }

    /** PR raised + check-in accuracy for one employee, over "cycle" (current 26th-25th payroll
     *  cycle), "30d" (rolling last 30 days) or "all" (lifetime). Only meaningful for employees
     *  linked to an app login — an unlinked (never logged in / pre-app) employee gets zeros. */
    public Map<String, Object> stats(Long id, String range) {
        Employee e = get(id);
        LocalDate[] bounds = rangeBounds(range);
        LocalDate start = bounds[0], end = bounds[1];

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("range", range);
        m.put("rangeStart", start);
        m.put("rangeEnd", end);

        String username = e.getLinkedUsername();
        if (username == null || username.isBlank()) {
            m.put("linked", false);
            m.put("prRaised", 0);
            m.put("correctCheckins", 0);
            m.put("incompleteCheckins", 0);
            return m;
        }
        m.put("linked", true);

        long prRaised = pwjEntryRepo.countByRaisedByAndCreatedAtBetween(
                e.getFullName(), start.atStartOfDay(), end.atTime(LocalTime.MAX));
        m.put("prRaised", prRaised);

        List<Attendance> records = attendanceRepo.findByUsernameAndWorkDateBetween(username, start, end);
        long correct = records.stream().filter(a -> a.getCheckInTime() != null && a.getCheckOutTime() != null).count();
        long incomplete = records.stream().filter(a -> a.getCheckInTime() != null && a.getCheckOutTime() == null).count();
        m.put("correctCheckins", correct);
        m.put("incompleteCheckins", incomplete);
        return m;
    }

    /** Every remark recorded for one day, across employees (HR dashboard). */
    public List<PerformanceRemark> remarksOn(LocalDate date) {
        return remarkRepo.findByRemarkDateOrderByIdDesc(date);
    }

    public List<PerformanceRemark> remarkHistory(Long employeeId) {
        return remarkRepo.findByEmployeeIdOrderByRemarkDateDescIdDesc(employeeId);
    }

    /** Appends a remark — several per employee per day are allowed. Remarks on management
     *  employees can only be added by the CEO. */
    @Transactional
    public PerformanceRemark addRemark(Long employeeId, LocalDate date, Integer rating, String notes, String actionBy, String actorRole) {
        Employee emp = get(employeeId); // 404s if the employee doesn't exist
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new RuntimeException("Rating must be between 1 and 5");
        }
        if (rating == null && (notes == null || notes.isBlank())) {
            throw new RuntimeException("Add a comment or a rating");
        }
        if (!List.of("ADMIN", "VP", "OH", "CEO").contains(actorRole == null ? "" : actorRole.toUpperCase())) {
            throw new RuntimeException("Only Admin/VP/OH/CEO can add performance remarks");
        }
        if (isManagement(emp) && !"CEO".equalsIgnoreCase(actorRole)) {
            throw new RuntimeException("Only the CEO can add remarks for management");
        }
        return remarkRepo.save(PerformanceRemark.builder()
                .employeeId(employeeId).remarkDate(date).rating(rating).notes(notes).createdBy(actionBy).build());
    }

    /** The employee a remark is about may appeal it once, within 24h of it being recorded;
     *  after that it stays as recorded. */
    @Transactional
    public PerformanceRemark appealRemark(Long employeeId, Long remarkId, String text, String username) {
        Employee emp = get(employeeId);
        PerformanceRemark r = remarkRepo.findById(remarkId)
                .filter(x -> x.getEmployeeId().equals(employeeId))
                .orElseThrow(() -> new RuntimeException("Remark not found"));
        if (emp.getLinkedUsername() == null || username == null || !emp.getLinkedUsername().equalsIgnoreCase(username)) {
            throw new RuntimeException("Only the employee can appeal their own remark");
        }
        if (text == null || text.isBlank()) throw new RuntimeException("Enter the reason for your appeal");
        if (r.getAppealedAt() != null) throw new RuntimeException("This remark has already been appealed");
        if (r.getCreatedAt() == null || java.time.LocalDateTime.now().isAfter(r.getCreatedAt().plusHours(24))) {
            throw new RuntimeException("The 24-hour appeal window has closed; the remark stands as recorded");
        }
        r.setAppealText(text.trim());
        r.setAppealedAt(java.time.LocalDateTime.now());
        return remarkRepo.save(r);
    }

    private boolean isManagement(Employee e) {
        return e.getDepartment() != null && e.getDepartment().toLowerCase().contains("management");
    }

    private LocalDate[] rangeBounds(String range) {
        LocalDate today = LocalDate.now();
        if ("30d".equalsIgnoreCase(range)) {
            return new LocalDate[]{today.minusDays(29), today};
        }
        if ("all".equalsIgnoreCase(range)) {
            return new LocalDate[]{LocalDate.of(2017, 1, 1), today};
        }
        // "cycle" (default): the 26th-25th payroll cycle containing today — cycle end is the
        // 25th of this month if today is on/before the 25th, else the 25th of next month.
        LocalDate end = (today.getDayOfMonth() <= 25 ? today : today.plusMonths(1)).withDayOfMonth(25);
        LocalDate start = end.minusMonths(1).withDayOfMonth(26);
        return new LocalDate[]{start, end};
    }
}
