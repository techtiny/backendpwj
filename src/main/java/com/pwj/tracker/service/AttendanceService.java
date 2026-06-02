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
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final AppUserRepository userRepository;

    @Transactional
    public Attendance checkIn(String username, Double lat, Double lng, String address) {
        AppUser user = userRepository.findByUsernameAndActiveTrue(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        LocalDate today = LocalDate.now();
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

        att.setCheckInTime(LocalDateTime.now());
        att.setCheckInLat(lat);
        att.setCheckInLng(lng);
        att.setCheckInAddress(address);
        att.setStatus("PRESENT");
        return attendanceRepository.save(att);
    }

    @Transactional
    public Attendance checkOut(String username, Double lat, Double lng, String address) {
        LocalDate today = LocalDate.now();
        Attendance att = attendanceRepository.findByUsernameAndWorkDate(username, today)
                .orElseThrow(() -> new RuntimeException("No check-in found for today"));

        if (att.getCheckInTime() == null) throw new RuntimeException("Must check in first");
        if (att.getCheckOutTime() != null) throw new RuntimeException("Already checked out today");

        LocalDateTime now = LocalDateTime.now();
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
        return attendanceRepository.findByUsernameAndWorkDate(username, LocalDate.now());
    }

    public List<Attendance> getUserHistory(String username) {
        return attendanceRepository.findByUsernameOrderByWorkDateDesc(username);
    }

    public List<Attendance> getTodayAll() {
        return attendanceRepository.findByWorkDateOrderByFullNameAsc(LocalDate.now());
    }

    public List<Attendance> getAll() {
        return attendanceRepository.findAllByOrderByWorkDateDescCheckInTimeDesc();
    }

    public Map<String, Object> getSummary(String username) {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        long presentDays = attendanceRepository.countByUsernameAndStatusSince(username, "PRESENT", monthStart);
        long halfDays    = attendanceRepository.countByUsernameAndStatusSince(username, "HALF_DAY", monthStart);
        long totalDays   = attendanceRepository.countByUsernameSince(username, monthStart);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("presentDays", presentDays);
        m.put("halfDays", halfDays);
        m.put("totalDays", totalDays);
        m.put("absentDays", Math.max(0, LocalDate.now().getDayOfMonth() - totalDays));
        return m;
    }
}
