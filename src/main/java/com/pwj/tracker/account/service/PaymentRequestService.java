package com.pwj.tracker.account.service;

import com.pwj.tracker.account.dto.ExpenseItemDto;
import com.pwj.tracker.account.entity.PaymentRequest;
import com.pwj.tracker.account.repository.PaymentRequestRepository;
import com.pwj.tracker.dto.PwjEntryResponse;
import com.pwj.tracker.model.AppUser;
import com.pwj.tracker.repository.AppUserRepository;
import com.pwj.tracker.service.PwjEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Payment requests on PO / WO documents: the engineer who raised the PR requests payment, the procurement person the PR
 * is allocated to approves it, and only then does it become a Send for Payment entry (Accounts approval levels follow).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentRequestService {

    private static final Set<String> TYPES = Set.of("PO", "WO");
    private static final Set<AppUser.Role> OVERSIGHT = EnumSet.of(AppUser.Role.ADMIN, AppUser.Role.VP, AppUser.Role.OH, AppUser.Role.CEO);

    private final PaymentRequestRepository repo;
    private final PwjEntryService pwjEntryService;
    private final ExpenseItemService expenseItemService;
    private final AppUserRepository userRepo;

    /** The engineer raises a payment request — validated like a direct send, but it only goes to procurement. */
    @Transactional
    public Map<String, Object> raise(String username, Long pwjEntryId, BigDecimal amount, String remarks,
                                     String paymentMadeAgainst, String paymentStage, String docNumber) {
        AppUser actor = actor(username);
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Amount must be greater than zero");
        PwjEntryResponse entry = pwjEntryService.getById(pwjEntryId);
        if (entry.getPwjType() == null || !TYPES.contains(entry.getPwjType().toUpperCase()))
            throw new IllegalArgumentException("Payment can be requested only for PO and WO documents");
        boolean issued = Boolean.TRUE.equals(entry.getPwjIssued())
                || (entry.getDocStatus() != null && "VP_APPROVED".equals(entry.getDocStatus().name()));
        if (!issued) throw new IllegalArgumentException("PR #" + pwjEntryId + " has not been issued yet");
        if (!same(entry.getRaisedBy(), actor))
            throw new IllegalArgumentException("Only the engineer who raised this PR can request payment for it");
        if (entry.getAllocatedTo() == null || entry.getAllocatedTo().isBlank())
            throw new IllegalArgumentException("This PR isn't allocated to a procurement person yet — they approve payment requests");

        String doc = blankToNull(docNumber);
        for (PaymentRequest p : repo.findByPwjEntryIdAndStatus(pwjEntryId, "PENDING")) {
            if (Objects.equals(blankToNull(p.getDocNumber()), doc))
                throw new IllegalArgumentException("A payment request of ₹" + p.getAmount() + " is already waiting for procurement approval");
        }
        // Same cap / pending-approval rules the Send for Payment step enforces — checked now so the engineer finds out early.
        Map<String, Object> avail = expenseItemService.getPwjEntryPaymentAvailability(pwjEntryId, doc);
        if (avail.get("pendingRequestId") != null)
            throw new IllegalArgumentException("An earlier payment for this document is still pending approval in Accounts — wait until it is approved or rejected");
        if (Boolean.TRUE.equals(avail.get("resolved")) && amount.compareTo(new BigDecimal(String.valueOf(avail.get("available")))) > 0)
            throw new IllegalArgumentException("₹" + amount + " is more than the ₹" + avail.get("available") + " still available on this document");

        PaymentRequest r = repo.save(PaymentRequest.builder()
                .pwjEntryId(pwjEntryId).docNumber(doc).amount(amount)
                .remarks(blankToNull(remarks)).paymentMadeAgainst(blankToNull(paymentMadeAgainst)).paymentStage(blankToNull(paymentStage))
                .requestedBy(actor.getUsername()).requestedByName(actor.getFullName()).status("PENDING").build());
        return view(r, entry, actor, new HashMap<>());
    }

    /** The procurement person the PR is allocated to approves: the request becomes a real Send for Payment entry. */
    @Transactional
    public Map<String, Object> approve(Long id, String username) {
        AppUser actor = actor(username);
        PaymentRequest r = get(id);
        PwjEntryResponse entry = pwjEntryService.getById(r.getPwjEntryId());
        requireAllocated(entry, actor);
        if (!"PENDING".equals(r.getStatus())) throw new IllegalArgumentException("This request was already " + r.getStatus().toLowerCase());
        // All the real checks (issued, PO-value cap, one open request per document) run here; any failure keeps it PENDING.
        ExpenseItemDto item = expenseItemService.sendPwjEntryForPayment(r.getPwjEntryId(), r.getAmount(), r.getRemarks(),
                r.getPaymentMadeAgainst(), r.getPaymentStage(), r.getDocNumber());
        r.setStatus("APPROVED");
        r.setDecidedBy(actor.getFullName()); r.setDecidedAt(LocalDateTime.now()); r.setExpenseItemId(item.getId());
        return view(repo.save(r), entry, actor, new HashMap<>());
    }

    @Transactional
    public Map<String, Object> reject(Long id, String username, String remark) {
        AppUser actor = actor(username);
        if (remark == null || remark.isBlank()) throw new IllegalArgumentException("Enter a reason for rejecting");
        PaymentRequest r = get(id);
        PwjEntryResponse entry = pwjEntryService.getById(r.getPwjEntryId());
        requireAllocated(entry, actor);
        if (!"PENDING".equals(r.getStatus())) throw new IllegalArgumentException("This request was already " + r.getStatus().toLowerCase());
        r.setStatus("REJECTED");
        r.setDecidedBy(actor.getFullName()); r.setDecidedAt(LocalDateTime.now()); r.setDecisionRemark(remark.trim());
        return view(repo.save(r), entry, actor, new HashMap<>());
    }

    /** The requesting engineer withdraws a request that is still waiting. */
    @Transactional
    public Map<String, Object> cancel(Long id, String username) {
        AppUser actor = actor(username);
        PaymentRequest r = get(id);
        if (!r.getRequestedBy().equalsIgnoreCase(actor.getUsername())) throw new IllegalArgumentException("Not your request");
        if (!"PENDING".equals(r.getStatus())) throw new IllegalArgumentException("Only a request that is still waiting can be cancelled");
        r.setStatus("CANCELLED");
        return view(repo.save(r), pwjEntryService.getById(r.getPwjEntryId()), actor, new HashMap<>());
    }

    /** Requests the user is involved in: their own (as the engineer), the ones waiting on / decided by them as the
     *  allocated procurement person, or everything for Admin / VP / OH / CEO (read-only). Newest first. */
    public List<Map<String, Object>> list(String username) {
        AppUser actor = actor(username);
        Map<Long, PwjEntryResponse> entries = new HashMap<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (PaymentRequest r : repo.findAllByOrderByRequestedAtDesc()) {
            PwjEntryResponse e = entries.computeIfAbsent(r.getPwjEntryId(), k -> {
                try { return pwjEntryService.getById(k); } catch (Exception ex) { return null; }
            });
            if (e == null) continue;
            boolean mine = r.getRequestedBy().equalsIgnoreCase(actor.getUsername());
            boolean allocated = same(e.getAllocatedTo(), actor);
            if (!mine && !allocated && !OVERSIGHT.contains(actor.getRole())) continue;
            out.add(view(r, e, actor, new HashMap<>()));
        }
        return out;
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private Map<String, Object> view(PaymentRequest r, PwjEntryResponse e, AppUser actor, Map<String, Object> scratch) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId()); m.put("pwjEntryId", r.getPwjEntryId()); m.put("docNumber", r.getDocNumber());
        m.put("amount", r.getAmount()); m.put("remarks", r.getRemarks());
        m.put("paymentMadeAgainst", r.getPaymentMadeAgainst()); m.put("paymentStage", r.getPaymentStage());
        m.put("requestedBy", r.getRequestedBy()); m.put("requestedByName", r.getRequestedByName()); m.put("requestedAt", r.getRequestedAt());
        m.put("status", r.getStatus());
        m.put("decidedBy", r.getDecidedBy()); m.put("decidedAt", r.getDecidedAt()); m.put("decisionRemark", r.getDecisionRemark());
        m.put("projectName", e.getProjectName()); m.put("material", e.getMaterialRequired()); m.put("pwjType", e.getPwjType());
        m.put("entryDocNumber", e.getDocNumber());
        m.put("approverName", e.getAllocatedToName() != null ? e.getAllocatedToName() : e.getAllocatedTo());
        m.put("canDecide", "PENDING".equals(r.getStatus()) && same(e.getAllocatedTo(), actor));
        m.put("canCancel", "PENDING".equals(r.getStatus()) && r.getRequestedBy().equalsIgnoreCase(actor.getUsername()));
        return m;
    }

    private void requireAllocated(PwjEntryResponse entry, AppUser actor) {
        if (!same(entry.getAllocatedTo(), actor))
            throw new IllegalArgumentException("Only the procurement person this PR is allocated to can decide this request");
    }

    private static boolean same(String name, AppUser u) {
        return name != null && (name.equalsIgnoreCase(u.getUsername()) || name.equalsIgnoreCase(u.getFullName()));
    }

    private PaymentRequest get(Long id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Payment request not found"));
    }

    private AppUser actor(String username) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Not signed in");
        return userRepo.findByUsernameAndActiveTrue(username).orElseThrow(() -> new IllegalArgumentException("Unknown user: " + username));
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
