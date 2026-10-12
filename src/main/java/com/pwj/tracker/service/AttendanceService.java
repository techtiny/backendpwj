package com.pwj.tracker.service;

import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.model.Attendance;
import com.pwj.tracker.model.LeaveRequest;
import com.pwj.tracker.repository.AppUserRepository;
import com.pwj.tracker.repository.AttendanceRepository;
import com.pwj.tracker.repository.HolidayRepository;
import com.pwj.tracker.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final AttendanceRepository attendanceRepository;
    private final AppUserRepository userRepository;
    private final HolidayRepository holidayRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    @Transactional
    public Attendance checkIn(String username, Double lat, Double lng, String address) {
        AppUser user = userRepository.findByUsernameAndActiveTrue(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        LocalDate today = LocalDate.now(IST);
        Optional<Attendance> existing = attendanceRepository.findByUsernameAndWorkDate(username, today);
        if (existing.isPresent() && existing.get().getCheckInTime() != null) {
            throw new RuntimeException("Already checked in today");
        }

        Attendance att = existing.orElse(Attendance.builder()
                .username(username)
                .fullName(user.getFullName())
                .workDate(today)
                .status("PRESENT")
                .build());

        att.setCheckInTime(LocalDateTime.now(IST));
        att.setCheckInLat(lat);
        att.setCheckInLng(lng);
        att.setCheckInAddress(address);
        att.setStatus("PRESENT");
        return attendanceRepository.save(att);
    }

    @Transactional
    public Attendance checkOut(String username, Double lat, Double lng, String address) {
        LocalDate today = LocalDate.now(IST);
        Attendance att = attendanceRepository.findByUsernameAndWorkDate(username, today)
                .orElseThrow(() -> new RuntimeException("No check-in found for today"));

        if (att.getCheckInTime() == null) throw new RuntimeException("Must check in first");
        if (att.getCheckOutTime() != null) throw new RuntimeException("Already checked out today");

        LocalDateTime now = LocalDateTime.now(IST); // IST — Railway server runs UTC
        att.setCheckOutTime(now);
        att.setCheckOutLat(lat);
        att.setCheckOutLng(lng);
        att.setCheckOutAddress(address);

        long minutes = java.time.Duration.between(att.getCheckInTime(), now).toMinutes();
        att.setTotalMinutes((int) minutes);
        att.setStatus(minutes >= 240 ? "PRESENT" : "HALF_DAY");
        return attendanceRepository.save(att);
    }

    public Optional<Attendance> getTodayRecord(String username) {
        return attendanceRepository.findByUsernameAndWorkDate(username, LocalDate.now(IST));
    }

    public List<Attendance> getUserHistory(String username) {
        List<Attendance> real = attendanceRepository.findByUsernameOrderByWorkDateDesc(username);
        return fillLeaveGaps(real, null);
    }

    public List<Attendance> getTodayAll() {
        List<Attendance> real = attendanceRepository.findByWorkDateOrderByFullNameAsc(LocalDate.now(IST));
        return fillLeaveGaps(real, LocalDate.now(IST));
    }

    public List<Attendance> getAll() {
        List<Attendance> real = attendanceRepository.findAllByOrderByWorkDateDescCheckInTimeDesc();
        return fillLeaveGaps(real, null);
    }

    public List<Attendance> getFieldStaffAttendance() {
        List<AppUser.Role> fieldRoles = List.of(AppUser.Role.ENGINEER, AppUser.Role.PROJECT_MANAGER, AppUser.Role.ADMIN, AppUser.Role.PROCUREMENT);
        List<String> usernames = userRepository.findByRoleInAndActiveTrue(fieldRoles)
                .stream().map(AppUser::getUsername).toList();
        if (usernames.isEmpty()) return List.of();
        List<Attendance> real = attendanceRepository.findByUsernameInOrderByWorkDateDescCheckInTimeDesc(usernames);
        return fillLeaveGaps(real, null);
    }

    /**
     * An employee on approved leave with no check-in that day previously showed
     * up as a silent gap — nothing in the list at all. This fills in a synthetic
     * (never persisted) ABSENT row for every approved-leave date that has no
     * real Attendance row, so leave days are visible instead of invisible.
     * Sundays/holidays and PERMISSION/HALF_DAY leave (the employee still
     * attends most of the day) are excluded — only a genuinely missing row on
     * an otherwise-working day gets a synthetic placeholder. Pass `onlyDate`
     * to scope this to a single day (e.g. "today"); null fills every date in
     * range for every approved leave found.
     */
    private List<Attendance> fillLeaveGaps(List<Attendance> real, LocalDate onlyDate) {
        Set<String> covered = real.stream()
                .map(a -> a.getUsername() + "|" + a.getWorkDate())
                .collect(Collectors.toSet());

        List<LeaveRequest> approved = leaveRequestRepository.findByStatusOrderByCreatedAtDesc("APPROVED");
        if (approved.isEmpty()) return real;

        List<Attendance> merged = new ArrayList<>(real);
        long syntheticId = -1;
        for (LeaveRequest lr : approved) {
            if ("PERMISSION".equals(lr.getLeaveType()) || "HALF_DAY".equals(lr.getLeaveType())) continue;
            for (LocalDate d = lr.getFromDate(); !d.isAfter(lr.getToDate()); d = d.plusDays(1)) {
                if (onlyDate != null && !d.equals(onlyDate)) continue;
                if (d.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
                String key = lr.getUsername() + "|" + d;
                if (!covered.add(key)) continue; // real row, or already-added synthetic for an overlapping leave
                if (holidayRepository.existsByDate(d)) continue;
                merged.add(Attendance.builder()
                        .id(syntheticId--)
                        .username(lr.getUsername())
                        .fullName(lr.getFullName())
                        .workDate(d)
                        .status("ABSENT")
                        .notes("On approved " + lr.getLeaveType() + " leave")
                        .build());
            }
        }
        merged.sort(Comparator.comparing(Attendance::getWorkDate).reversed());
        return merged;
    }

    /** Mark past-day records with no check-out as ABSENT, then return them for review. */
    @Transactional
    public List<Attendance> getIncompleteRecords() {
        LocalDate today = LocalDate.now(IST);
        List<Attendance> incomplete = attendanceRepository.findIncompleteBeforeDate(today);
        for (Attendance a : incomplete) {
            if (!"ABSENT".equals(a.getStatus())) {
                a.setStatus("ABSENT");
                attendanceRepository.save(a);
            }
        }
        return incomplete;
    }

    /** Admin correction: update checkIn/checkOut times and recalculate duration/status. */
    @Transactional
    public Attendance updateAttendance(Long id, LocalDateTime checkInTime, LocalDateTime checkOutTime, String notes) {
        Attendance att = attendanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Attendance record not found: " + id));
        if (checkInTime  != null) att.setCheckInTime(checkInTime);
        if (checkOutTime != null) att.setCheckOutTime(checkOutTime);
        if (notes        != null) att.setNotes(notes);
        if (att.getCheckInTime() != null && att.getCheckOutTime() != null) {
            long minutes = java.time.Duration.between(att.getCheckInTime(), att.getCheckOutTime()).toMinutes();
            att.setTotalMinutes((int) minutes);
            att.setStatus(minutes >= 240 ? "PRESENT" : "HALF_DAY");
        }
        return attendanceRepository.save(att);
    }

    public Map<String, Object> getSummary(String username) {
        LocalDate today = LocalDate.now(IST);
        LocalDate monthStart = today.withDayOfMonth(1);
        long presentDays = attendanceRepository.countByUsernameAndStatusSince(username, "PRESENT", monthStart);
        long halfDays    = attendanceRepository.countByUsernameAndStatusSince(username, "HALF_DAY", monthStart);
        long totalDays   = attendanceRepository.countByUsernameSince(username, monthStart);
        long holidayDays = holidayRepository.findByDateBetweenOrderByDateAsc(monthStart, today).size();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("presentDays", presentDays);
        m.put("halfDays", halfDays);
        m.put("totalDays", totalDays);
        m.put("holidayDays", holidayDays);
        // Declared holidays don't require a check-in, so they're excluded from the absent count.
        m.put("absentDays", Math.max(0, today.getDayOfMonth() - totalDays - holidayDays));
        return m;
    }
}
