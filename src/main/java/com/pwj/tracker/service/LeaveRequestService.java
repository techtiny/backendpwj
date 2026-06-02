package com.pwj.tracker.service;

import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.model.LeaveRequest;
import com.pwj.tracker.repository.AppUserRepository;
import com.pwj.tracker.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRepository;
    private final AppUserRepository userRepository;

    @Transactional
    public LeaveRequest apply(String username, String leaveType, LocalDate fromDate,
                              LocalDate toDate, String reason, String attachmentUrl) {
        AppUser user = userRepository.findByUsernameAndActiveTrue(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (fromDate.isAfter(toDate)) throw new RuntimeException("From date must be before to date");

        int days = (int) ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        LeaveRequest req = LeaveRequest.builder()
                .username(username)
                .fullName(user.getFullName())
                .leaveType(leaveType)
                .fromDate(fromDate)
                .toDate(toDate)
                .totalDays(days)
                .reason(reason)
                .attachmentUrl(attachmentUrl)
                .status("PENDING")
                .build();
        return leaveRepository.save(req);
    }

    @Transactional
    public LeaveRequest approve(Long id, String approvedBy, String comment) {
        LeaveRequest req = leaveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));
        if (!"PENDING".equals(req.getStatus())) throw new RuntimeException("Request already processed");
        req.setStatus("APPROVED");
        req.setApprovedBy(approvedBy);
        req.setApprovalComment(comment);
        req.setApprovedAt(LocalDateTime.now());
        return leaveRepository.save(req);
    }

    @Transactional
    public LeaveRequest reject(Long id, String approvedBy, String comment) {
        LeaveRequest req = leaveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));
        if (!"PENDING".equals(req.getStatus())) throw new RuntimeException("Request already processed");
        req.setStatus("REJECTED");
        req.setApprovedBy(approvedBy);
        req.setApprovalComment(comment);
        req.setApprovedAt(LocalDateTime.now());
        return leaveRepository.save(req);
    }

    @Transactional
    public LeaveRequest cancel(Long id, String username) {
        LeaveRequest req = leaveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));
        if (!req.getUsername().equals(username)) throw new RuntimeException("Unauthorized");
        if (!"PENDING".equals(req.getStatus())) throw new RuntimeException("Can only cancel pending requests");
        req.setStatus("CANCELLED");
        return leaveRepository.save(req);
    }

    public List<LeaveRequest> getMyLeaves(String username) {
        return leaveRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    public List<LeaveRequest> getPending() {
        return leaveRepository.findByStatusOrderByCreatedAtDesc("PENDING");
    }

    public List<LeaveRequest> getAll() {
        return leaveRepository.findAllByOrderByCreatedAtDesc();
    }

    public Map<String, Object> getMySummary(String username) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pending",  leaveRepository.countByUsernameAndStatus(username, "PENDING"));
        m.put("approved", leaveRepository.countByUsernameAndStatus(username, "APPROVED"));
        m.put("rejected", leaveRepository.countByUsernameAndStatus(username, "REJECTED"));
        return m;
    }
}
