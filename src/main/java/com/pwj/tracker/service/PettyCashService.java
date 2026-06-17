package com.pwj.tracker.service;

import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.model.PettyCash;
import com.pwj.tracker.repository.AppUserRepository;
import com.pwj.tracker.repository.PettyCashRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PettyCashService {

    private final PettyCashRepository repo;
    private final AppUserRepository   userRepo;

    @Transactional
    public PettyCash create(String username, LocalDate expenseDate, String category,
                            String description, BigDecimal amount, String paymentMode,
                            String attachmentUrl, String projectName) {
        AppUser user = userRepo.findByUsernameAndActiveTrue(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        if (projectName != null && !projectName.isBlank()
                && repo.countActiveRequestsForProject(username, projectName) > 0) {
            throw new RuntimeException("ACTIVE_REQUEST_EXISTS:" + projectName);
        }

        return repo.save(PettyCash.builder()
                .username(username)
                .fullName(user.getFullName())
                .expenseDate(expenseDate != null ? expenseDate : LocalDate.now())
                .category(category)
                .description(description)
                .amount(amount)
                .paymentMode(paymentMode)
                .attachmentUrl(attachmentUrl)
                .projectName(projectName)
                .status("PENDING")
                .build());
    }

    public List<PettyCash> getMyEntries(String username) {
        return repo.findByUsernameOrderByExpenseDateDescCreatedAtDesc(username);
    }

    public List<PettyCash> getPending() {
        return repo.findByStatusOrderByCreatedAtDesc("PENDING");
    }

    public List<PettyCash> getAll() {
        return repo.findAllByOrderByExpenseDateDescCreatedAtDesc();
    }

    @Transactional
    public PettyCash approve(Long id, String approvedBy, String approvedByRole, String comment) {
        PettyCash entry = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        if (!"PENDING".equals(entry.getStatus()))
            throw new RuntimeException("Entry is not pending");
        entry.setStatus("APPROVED");
        entry.setApprovedBy(approvedBy);
        entry.setApprovedByRole(approvedByRole);
        entry.setApprovalComment(comment);
        entry.setApprovedAt(LocalDateTime.now());
        return repo.save(entry);
    }

    @Transactional
    public PettyCash reject(Long id, String approvedBy, String approvedByRole, String comment) {
        PettyCash entry = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        if (!"PENDING".equals(entry.getStatus()))
            throw new RuntimeException("Entry is not pending");
        entry.setStatus("REJECTED");
        entry.setApprovedBy(approvedBy);
        entry.setApprovedByRole(approvedByRole);
        entry.setApprovalComment(comment);
        entry.setApprovedAt(LocalDateTime.now());
        return repo.save(entry);
    }

    @Transactional
    public PettyCash markTransferred(Long id) {
        PettyCash entry = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        if (!"APPROVED".equals(entry.getStatus()))
            throw new RuntimeException("Entry is not approved");
        entry.setStatus("CASH_TRANSFERRED");
        entry.setCashTransferredAt(LocalDateTime.now());
        return repo.save(entry);
    }

    @Transactional
    public PettyCash submitProof(Long id, String username, java.util.List<String> proofUrlList) {
        PettyCash entry = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        if (!entry.getUsername().equals(username))
            throw new RuntimeException("Unauthorized");
        if (!"CASH_TRANSFERRED".equals(entry.getStatus()))
            throw new RuntimeException("Cash not yet transferred for this entry");
        if (proofUrlList == null || proofUrlList.isEmpty())
            throw new RuntimeException("At least one proof document is required");
        entry.setStatus("PROOF_SUBMITTED");
        entry.setProofUrl(proofUrlList.get(0));
        try {
            entry.setProofUrls(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(proofUrlList));
        } catch (Exception e) {
            entry.setProofUrls("[\"" + proofUrlList.get(0) + "\"]");
        }
        entry.setProofSubmittedAt(LocalDateTime.now());
        return repo.save(entry);
    }

    @Transactional
    public void delete(Long id, String username) {
        PettyCash entry = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        if (!entry.getUsername().equals(username))
            throw new RuntimeException("Unauthorized");
        if (!"PENDING".equals(entry.getStatus()))
            throw new RuntimeException("Only pending entries can be deleted");
        repo.delete(entry);
    }

    public Map<String, Object> getSummary(String username) {
        BigDecimal approved = repo.sumByUsernameAndStatus(username, "APPROVED");
        BigDecimal pending  = repo.sumByUsernameAndStatus(username, "PENDING");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalApproved",  approved);
        m.put("totalPending",   pending);
        m.put("countPending",   repo.countByUsernameAndStatus(username, "PENDING"));
        m.put("countApproved",  repo.countByUsernameAndStatus(username, "APPROVED"));
        m.put("countRejected",  repo.countByUsernameAndStatus(username, "REJECTED"));
        return m;
    }
}
