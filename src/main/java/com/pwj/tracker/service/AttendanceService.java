package com.pwj.tracker.service;

import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.model.Attendance;
import com.pwj.tracker.repository.AppUserRepository;
import com.pwj.tracker.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final AttendanceRepository attendanceRepository;
    private final AppUserRepository userRepository;

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
        return attendanceRepository.findByUsernameOrderByWorkDateDesc(username);
    }

    public List<Attendance> getTodayAll() {
        return attendanceRepository.findByWorkDateOrderByFullNameAsc(LocalDate.now(IST));
    }

    public List<Attendance> getAll() {
        return attendanceRepository.findAllByOrderByWorkDateDescCheckInTimeDesc();
    }

    public List<Attendance> getFieldStaffAttendance() {
        List<AppUser.Role> fieldRoles = List.of(AppUser.Role.ENGINEER, AppUser.Role.PROJECT_MANAGER);
        List<String> usernames = userRepository.findByRoleInAndActiveTrue(fieldRoles)
                .stream().map(AppUser::getUsername).toList();
        if (usernames.isEmpty()) return List.of();
        return attendanceRepository.findByUsernameInOrderByWorkDateDescCheckInTimeDesc(usernames);
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
        LocalDate monthStart = LocalDate.now(IST).withDayOfMonth(1);
        long presentDays = attendanceRepository.countByUsernameAndStatusSince(username, "PRESENT", monthStart);
        long halfDays    = attendanceRepository.countByUsernameAndStatusSince(username, "HALF_DAY", monthStart);
        long totalDays   = attendanceRepository.countByUsernameSince(username, monthStart);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("presentDays", presentDays);
        m.put("halfDays", halfDays);
        m.put("totalDays", totalDays);
        m.put("absentDays", Math.max(0, LocalDate.now(IST).getDayOfMonth() - totalDays));
        return m;
    }
}
