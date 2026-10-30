package com.pwj.tracker.service;

import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.model.Attendance;
import com.pwj.tracker.model.Employee;
import com.pwj.tracker.model.PerformanceRemark;
import com.pwj.tracker.model.PwjEntry;
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

    /** Onboards a new employee into the master. personCode is generated (H-YY-NNNN, where NNNN continues the
     *  running sequence across years) when left blank; YY is the year of joining. */
    @Transactional
    public Employee create(Map<String, Object> in) {
        String name = str(in.get("fullName"));
        if (name == null) throw new RuntimeException("Full name is required");
        LocalDate doj = date(in.get("doj"));
        if (doj == null) throw new RuntimeException("Date of joining is required");
        String code = str(in.get("personCode"));
        if (code != null) {
            if (employeeRepo.existsByPersonCode(code)) throw new RuntimeException("Person code already exists: " + code);
        } else {
            int max = 0;
            for (Employee e : employeeRepo.findAll()) {
                if (e.getPersonCode() == null) continue;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^H-\\d{2}-(\\d+)$").matcher(e.getPersonCode());
                if (m.matches()) max = Math.max(max, Integer.parseInt(m.group(1)));
            }
            code = String.format("H-%02d-%04d", doj.getYear() % 100, max + 1);
        }
        String username = str(in.get("linkedUsername"));
        if (username != null) {
            userRepo.findByUsernameAndActiveTrue(username).orElseThrow(() -> new RuntimeException("No active app user: " + username));
            if (employeeRepo.findByLinkedUsername(username).isPresent()) throw new RuntimeException("That login is already linked to another employee");
        }
        Employee e = Employee.builder()
                .fullName(name).dob(date(in.get("dob"))).gender(str(in.get("gender")))
                .department(str(in.get("department"))).designation(str(in.get("designation")))
                .currentDesignation(str(in.get("designation")))
                .doj(doj).personCode(code).linkedUsername(username)
                .build();
        return employeeRepo.save(e);
    }

    /** Gives a login its Employee Master record (linked to it) when it has none — so new employees get a profile,
     *  and the HR dashboard can take remarks for them, without a second manual step. Joining date defaults to the
     *  day the login was created. Returns the existing or new record. */
    @Transactional
    public Employee ensureProfileForLogin(AppUser u) {
        return employeeRepo.findByLinkedUsername(u.getUsername()).orElseGet(() -> {
            Map<String, Object> in = new java.util.HashMap<>();
            in.put("fullName", u.getFullName());
            in.put("designation", u.getDesignation());
            in.put("doj", (u.getCreatedAt() != null ? u.getCreatedAt().toLocalDate() : LocalDate.now()).toString());
            in.put("linkedUsername", u.getUsername());
            return create(in);
        });
    }

    @Transactional
    public Employee createFromLogin(String username) {
        AppUser u = userRepo.findByUsernameAndActiveTrue(username)
                .orElseThrow(() -> new RuntimeException("No active app user: " + username));
        return ensureProfileForLogin(u);
    }

    /** A login marked exited from Happizo also leaves the Employee Master's active list: record its date of resignation. */
    @Transactional
    public void syncExit(String username, LocalDate exitDate) {
        if (username == null) return;
        employeeRepo.findByLinkedUsername(username).ifPresent(e -> {
            e.setDor(exitDate != null ? exitDate : LocalDate.now());
            if (e.getDesignationAtExit() == null) e.setDesignationAtExit(e.getDesignation());
            employeeRepo.save(e);
        });
    }

    /** A wrongly-exited login was reinstated — clear the exit date that syncExit recorded (a different, older exit is left alone). */
    @Transactional
    public void syncReinstate(String username, LocalDate exitDate) {
        if (username == null) return;
        employeeRepo.findByLinkedUsername(username).ifPresent(e -> {
            if (e.getDor() != null && (exitDate == null || e.getDor().equals(exitDate))) {
                e.setDor(null);
                employeeRepo.save(e);
            }
        });
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static LocalDate date(Object o) {
        String s = str(o);
        return s == null ? null : LocalDate.parse(s);
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
            m.put("prClosed", 0);
            m.put("correctCheckins", 0);
            m.put("incompleteCheckins", 0);
            return m;
        }
        m.put("linked", true);

        long prRaised = pwjEntryRepo.countByRaisedByAndCreatedAtBetween(
                e.getFullName(), start.atStartOfDay(), end.atTime(LocalTime.MAX));
        m.put("prRaised", prRaised);
        m.put("prClosed", pwjEntryRepo.countByRaisedByAndStatusAndCreatedAtBetween(
                e.getFullName(), PwjEntry.EntryStatus.CLOSED, start.atStartOfDay(), end.atTime(LocalTime.MAX)));

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
        return addRemark(employeeId, date, rating, notes, actionBy, actorRole, null, null);
    }

    /** Performance comment with a KPI category + parameter (scored), or a plain comment when category is null. */
    @Transactional
    public PerformanceRemark addRemark(Long employeeId, LocalDate date, Integer rating, String notes, String actionBy, String actorRole,
                                       String parameter, String category) {
        Employee emp = get(employeeId); // 404s if the employee doesn't exist
        if (category != null && !category.isBlank()) {
            category = category.trim().toUpperCase();
            if (!PerformanceRemark.CATEGORY_WEIGHTS.containsKey(category)) throw new RuntimeException("Unknown category: " + category);
            if (rating == null) throw new RuntimeException("Pick a rating (1-5)");
        } else {
            category = null;
        }
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
                .employeeId(employeeId).remarkDate(date).rating(rating).notes(notes).createdBy(actionBy).createdByRole(actorRole.toUpperCase())
                .parameter(parameter == null || parameter.isBlank() ? null : parameter.trim()).category(category).build());
    }

    /** The employee a remark is about may appeal it once, within 48h of it being recorded;
     *  after that it is closed and a reviewer (Admin/VP/OH/CEO) sets the final star rating. */
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
        if (r.getAcknowledgedAt() != null) throw new RuntimeException("You already accepted this comment");
        if (r.getCreatedAt() == null || java.time.LocalDateTime.now().isAfter(r.getCreatedAt().plusHours(48))) {
            throw new RuntimeException("The 48-hour appeal window has closed; a reviewer will finalise the rating");
        }
        r.setAppealText(text.trim());
        r.setAppealedAt(java.time.LocalDateTime.now());
        r.setAppealStatus("PENDING");
        return remarkRepo.save(r);
    }

    /** Employee ticks (accepts) a comment instead of appealing it. */
    @Transactional
    public PerformanceRemark acknowledgeRemark(Long employeeId, Long remarkId, String username) {
        Employee emp = get(employeeId);
        PerformanceRemark r = remarkRepo.findById(remarkId)
                .filter(x -> x.getEmployeeId().equals(employeeId))
                .orElseThrow(() -> new RuntimeException("Remark not found"));
        if (emp.getLinkedUsername() == null || username == null || !emp.getLinkedUsername().equalsIgnoreCase(username)) {
            throw new RuntimeException("Only the employee can accept their own comment");
        }
        if (r.getAppealedAt() != null) throw new RuntimeException("This comment has already been appealed");
        if (r.getAcknowledgedAt() != null) return r;
        r.setAcknowledgedAt(java.time.LocalDateTime.now());
        return remarkRepo.save(r);
    }

    /** Reviewer sets/updates the star rating on a remark — used once the employee's 48h window
     *  has passed without an appeal, or to settle an appeal. */
    @Transactional
    public PerformanceRemark rateRemark(Long employeeId, Long remarkId, Integer rating, String actionBy, String actorRole) {
        return rateRemark(employeeId, remarkId, rating, null, actionBy, actorRole);
    }

    /** Sets the final Rating + Category (→ Score) once the appeal stage is over: the employee accepted it,
     *  the 48h window passed with no appeal, or the appeal was rejected (✕). */
    @Transactional
    public PerformanceRemark rateRemark(Long employeeId, Long remarkId, Integer rating, String category, String actionBy, String actorRole) {
        Employee emp = get(employeeId);
        PerformanceRemark r = remarkRepo.findById(remarkId)
                .filter(x -> x.getEmployeeId().equals(employeeId))
                .orElseThrow(() -> new RuntimeException("Remark not found"));
        checkReviewer(emp, actorRole);
        if ("ACCEPTED".equals(r.getAppealStatus())) throw new RuntimeException("The appeal was accepted — this comment is withdrawn and isn't rated");
        if ("PENDING".equals(r.getAppealStatus())) throw new RuntimeException("Review the employee's appeal first (✓ / ✕)");
        if (r.getAppealedAt() == null && r.getAcknowledgedAt() == null && r.getCreatedAt() != null
                && java.time.LocalDateTime.now().isBefore(r.getCreatedAt().plusHours(48))) {
            throw new RuntimeException("Waiting for the employee to accept or appeal (48-hour window)");
        }
        applyRating(r, rating, category);
        r.setRatedBy(actionBy);
        return remarkRepo.save(r);
    }

    private void applyRating(PerformanceRemark r, Integer rating, String category) {
        if (rating == null || rating < 1 || rating > 5) throw new RuntimeException("Pick a rating (1-5 stars)");
        String c = category == null ? "" : category.trim().toUpperCase();
        if (!PerformanceRemark.CATEGORY_WEIGHTS.containsKey(c)) throw new RuntimeException("Select a category");
        r.setRating(rating);
        r.setCategory(c);
    }

    private void checkReviewer(Employee emp, String actorRole) {
        if (!List.of("ADMIN", "VP", "OH", "CEO").contains(actorRole == null ? "" : actorRole.toUpperCase())) {
            throw new RuntimeException("Only Admin/VP/OH/CEO can review performance comments");
        }
        if (isManagement(emp) && !"CEO".equalsIgnoreCase(actorRole)) {
            throw new RuntimeException("Only the CEO can review comments for management");
        }
    }

    /** Reviewer's verdict on an appeal: ACCEPT (tick — remark withdrawn from the score) or
     *  REJECT (cross — remark stands; a reply explaining why is required). */
    @Transactional
    public PerformanceRemark decideAppeal(Long employeeId, Long remarkId, String decision, String comment, String actionBy, String actorRole) {
        return decideAppeal(employeeId, remarkId, decision, comment, null, null, actionBy, actorRole);
    }

    /** ✓ withdraws the comment (never rated); ✕ rejects with the reviewer's remark — the comment then waits for ★ Rate. */
    @Transactional
    public PerformanceRemark decideAppeal(Long employeeId, Long remarkId, String decision, String comment, Integer rating, String category,
                                          String actionBy, String actorRole) {
        Employee emp = get(employeeId);
        PerformanceRemark r = remarkRepo.findById(remarkId)
                .filter(x -> x.getEmployeeId().equals(employeeId))
                .orElseThrow(() -> new RuntimeException("Remark not found"));
        if (!List.of("ADMIN", "VP", "OH", "CEO").contains(actorRole == null ? "" : actorRole.toUpperCase())) {
            throw new RuntimeException("Only Admin/VP/OH/CEO can review appeals");
        }
        if (isManagement(emp) && !"CEO".equalsIgnoreCase(actorRole)) {
            throw new RuntimeException("Only the CEO can review appeals from management");
        }
        if (r.getAppealedAt() == null) throw new RuntimeException("This remark has no appeal to review");
        if (r.getAppealStatus() != null && !"PENDING".equals(r.getAppealStatus())) throw new RuntimeException("This appeal has already been reviewed");
        String d = decision == null ? "" : decision.trim().toUpperCase();
        if (!d.equals("ACCEPT") && !d.equals("REJECT")) throw new RuntimeException("Decision must be ACCEPT or REJECT");
        if (d.equals("REJECT") && (comment == null || comment.isBlank())) throw new RuntimeException("Add a comment explaining why the appeal is not accepted");
        if (d.equals("ACCEPT")) { r.setRating(null); r.setCategory(null); }
        else if (rating != null && category != null) applyRating(r, rating, category); // optional — otherwise rated later via ★ Rate
        r.setAppealStatus(d.equals("ACCEPT") ? "ACCEPTED" : "REJECTED");
        r.setDecisionBy(actionBy);
        r.setDecisionRole(actorRole.toUpperCase());
        r.setDecisionAt(java.time.LocalDateTime.now());
        r.setDecisionComment(comment == null || comment.isBlank() ? null : comment.trim());
        return remarkRepo.save(r);
    }

    /** Appeals awaiting a reviewer's tick/cross, newest first, with the employee's name attached. */
    public List<Map<String, Object>> pendingAppeals() {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        List<PerformanceRemark> todo = new java.util.ArrayList<>(remarkRepo.findByAppealStatusOrderByAppealedAtDesc("PENDING"));
        // 48h passed without an appeal and still unrated → reviewer must set Rating + Category
        todo.addAll(remarkRepo.findAwaitingRating(java.time.LocalDateTime.now().minusHours(48), PerformanceRemark.REVIEW_FLOW_START));
        for (PerformanceRemark r : todo) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("remark", r);
            m.put("type", "PENDING".equals(r.getAppealStatus()) ? "APPEAL" : "RATE");
            employeeRepo.findById(r.getEmployeeId()).ifPresent(e -> {
                m.put("employeeName", e.getFullName());
                m.put("department", e.getDepartment());
            });
            out.add(m);
        }
        return out;
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
