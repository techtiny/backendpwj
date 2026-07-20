package com.pwj.tracker.account.service;

import com.pwj.tracker.account.dto.ExpenseItemDto;
import com.pwj.tracker.account.dto.SendForPaymentRequest;
import com.pwj.tracker.account.entity.ExpenseItem;
import com.pwj.tracker.account.repository.ExpenseItemRepository;
import com.pwj.tracker.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
public class ExpenseItemService {

    private final ExpenseItemRepository repo;
    private final ProjectRepository projectRepo;

    public ExpenseItemService(ExpenseItemRepository repo, ProjectRepository projectRepo) {
        this.repo = repo;
        this.projectRepo = projectRepo;
    }

    public List<ExpenseItemDto> getByProjectAndCategory(Long projectId, String category) {
        Map<String, BigDecimal[]> poCache = new HashMap<>();
        return repo.findByProjectIdAndCategoryOrderById(projectId, category.toUpperCase())
                .stream().map(e -> toDto(e, poCache)).toList();
    }

    public Map<String, Object> getSummary(Long projectId, String category) {
        String cat = category.toUpperCase();
        BigDecimal totalPwj    = repo.sumPwjTotalPayable(projectId, cat);
        BigDecimal totalVendor = repo.sumVendorTotalPayable(projectId, cat);
        BigDecimal totalGst    = repo.sumVendorGst(projectId, cat);
        BigDecimal totalPaid   = repo.sumPaid(projectId, cat);
        BigDecimal pwjGross    = totalPwj.subtract(totalGst);
        BigDecimal balPwj      = totalPwj.subtract(totalPaid);
        BigDecimal balActual   = totalVendor.subtract(totalPaid);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalGrossAsPwj",    pwjGross);
        m.put("totalPayableAsPwj",  totalPwj);
        m.put("totalGrossActual",   totalVendor.subtract(totalGst));
        m.put("totalGst",           totalGst);
        m.put("totalPayableActual", totalVendor);
        m.put("totalPaid",          totalPaid);
        m.put("balanceAsPwj",       balPwj);
        m.put("balanceAsActual",    balActual);
        return m;
    }

    public ExpenseItemDto create(ExpenseItemDto dto) {
        if ((dto.getCategory() == null || dto.getCategory().isBlank()) && dto.getPaymentAgainst() != null) {
            dto.setCategory(defaultCategoryFor(dto.getPaymentAgainst()));
        }
        validateCategoryForOrderType(dto.getPaymentAgainst(), dto.getCategory());
        ExpenseItem e = new ExpenseItem();
        apply(e, dto);
        e.setId(null);
        return toDto(repo.save(e));
    }

    public ExpenseItemDto update(Long id, ExpenseItemDto dto) {
        ExpenseItem e = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense item not found: " + id));
        String orderType   = dto.getPaymentAgainst() != null ? dto.getPaymentAgainst() : e.getPaymentAgainst();
        String newCategory = dto.getCategory() != null ? dto.getCategory().toUpperCase() : e.getCategory();
        validateCategoryForOrderType(orderType, newCategory);
        apply(e, dto);
        return toDto(repo.save(e));
    }

    private static final Map<String, List<String>> ALLOWED_CATEGORIES = Map.of(
        "PO", List.of("MATERIAL"),
        "WO", List.of("SUBCONTRACT", "LABOUR", "CONSULTANTS"),
        "JO", List.of("MATERIAL", "LABOUR", "MISCELLANEOUS")
    );

    private String defaultCategoryFor(String paymentAgainst) {
        return switch (orderTypePrefix(paymentAgainst)) {
            case "PO" -> "MATERIAL";
            case "WO" -> "SUBCONTRACT";
            case "JO" -> "MATERIAL";
            default   -> null;
        };
    }

    private void validateCategoryForOrderType(String paymentAgainst, String category) {
        if (paymentAgainst == null || category == null) return;
        String prefix = orderTypePrefix(paymentAgainst);
        List<String> allowed = ALLOWED_CATEGORIES.get(prefix);
        if (allowed != null && !allowed.contains(category.toUpperCase())) {
            throw new IllegalArgumentException(prefix + " entries can only be placed in: " + String.join(", ", allowed));
        }
    }

    private String orderTypePrefix(String paymentAgainst) {
        if (paymentAgainst == null) return "";
        String u = paymentAgainst.toUpperCase();
        if (u.startsWith("PO")) return "PO";
        if (u.startsWith("WO")) return "WO";
        if (u.startsWith("JO")) return "JO";
        return u;
    }

    public int repairCategories() {
        List<ExpenseItem> broken = repo.findUncategorized();
        int fixed = 0;
        for (ExpenseItem e : broken) {
            String cat = defaultCategoryFor(e.getPaymentAgainst());
            if (cat != null) { e.setCategory(cat); repo.save(e); fixed++; }
        }
        return fixed;
    }

    public List<String> getTrackedRefs(Long projectId) {
        return repo.findTrackedRefsByProjectId(projectId);
    }

    public ExpenseItemDto moveCategory(Long id, String newCategory) {
        if (newCategory == null || newCategory.isBlank())
            throw new IllegalArgumentException("Category is required");
        ExpenseItem e = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense item not found: " + id));
        validateCategoryForOrderType(e.getPaymentAgainst(), newCategory);
        e.setCategory(newCategory.toUpperCase());
        return toDto(repo.save(e));
    }

    public void delete(Long id) {
        if (!repo.existsById(id)) throw new IllegalArgumentException("Expense item not found: " + id);
        repo.deleteById(id);
    }

    // ── Payment eligibility / send-for-payment workflow ─────────────────────────

    public ExpenseItemDto setEligibility(Long id, boolean eligible) {
        ExpenseItem e = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense item not found: " + id));
        e.setEligibleForPayment(eligible);
        return toDto(repo.save(e));
    }

    /**
     * Sends the selected entries for payment. A FULL payment pays off the entry's
     * remaining balance; a PART payment pays the given amount. Either way, the
     * total paid across every entry sharing the same refNo (the actual PO/WO/JO
     * document — paymentAgainst is only the doc type) can never exceed that
     * document's derived value (sum of pwjTotalPayable for the group). Atomic:
     * if any entry in the batch fails validation, nothing in the batch is saved.
     */
    @Transactional
    public List<ExpenseItemDto> sendForPayment(List<SendForPaymentRequest.Item> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("No entries selected");
        }
        List<ExpenseItemDto> results = new ArrayList<>();
        for (SendForPaymentRequest.Item req : requests) {
            ExpenseItem e = repo.findById(req.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Expense item not found: " + req.getId()));
            if (!Boolean.TRUE.equals(e.getEligibleForPayment())) {
                throw new IllegalArgumentException("Entry #" + e.getId() + " is not marked eligible for payment");
            }
            BigDecimal remainingForEntry = safe(e.getPwjTotalPayable()).subtract(safe(e.getSentAmount()));
            if (remainingForEntry.signum() <= 0) {
                throw new IllegalArgumentException("Entry #" + e.getId() + " has already been fully sent for payment");
            }
            boolean isFull = "FULL".equalsIgnoreCase(req.getPaymentType());
            BigDecimal amount = isFull ? remainingForEntry : req.getAmount();
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("Entry #" + e.getId() + ": amount must be greater than zero");
            }
            if (amount.compareTo(remainingForEntry) > 0) {
                throw new IllegalArgumentException(
                        "Entry #" + e.getId() + ": amount exceeds this entry's remaining balance (" + remainingForEntry + ")");
            }

            if (e.getRefNo() != null && !e.getRefNo().isBlank()) {
                BigDecimal poValue       = repo.sumPwjTotalPayableForPo(e.getProjectId(), e.getRefNo());
                BigDecimal alreadySentPo = repo.sumSentForPo(e.getProjectId(), e.getRefNo());
                BigDecimal newTotalForPo = alreadySentPo.add(amount);
                if (newTotalForPo.compareTo(poValue) > 0) {
                    throw new IllegalArgumentException(
                            "Entry #" + e.getId() + ": sending " + amount + " would exceed the PO value (" + poValue +
                            ") for " + e.getRefNo() + " — already sent " + alreadySentPo);
                }
            }

            e.setSentAmount(safe(e.getSentAmount()).add(amount));
            BigDecimal newRemaining = safe(e.getPwjTotalPayable()).subtract(e.getSentAmount());
            e.setPaymentStatus(newRemaining.signum() <= 0 ? "FULL_PAYMENT_SENT" : "PART_PAYMENT_SENT");
            e.setSentAt(java.time.LocalDateTime.now());
            results.add(toDto(repo.save(e)));
        }
        return results;
    }

    /** Cross-project tracker: every entry that has been sent for payment (part or full), newest first. */
    public List<ExpenseItemDto> getSentForPayment() {
        Map<Long, String> names = new HashMap<>();
        projectRepo.findAll().forEach(p -> names.put(p.getId(), p.getName()));
        Map<String, BigDecimal[]> poCache = new HashMap<>();
        return repo.findSentForPayment().stream().map(e -> {
            ExpenseItemDto d = toDto(e, poCache);
            d.setProjectName(names.getOrDefault(e.getProjectId(), "Unknown"));
            return d;
        }).toList();
    }

    // ── OH → VP approval on the sent amount — independent of Send for Payment itself ──

    /** OH reviews a sent entry: may revise sentAmount, then Approve or Reject. */
    public ExpenseItemDto setOhApproval(Long id, String status, BigDecimal revisedAmount) {
        ExpenseItem e = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense item not found: " + id));
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            throw new IllegalArgumentException("status must be APPROVED or REJECTED");
        }
        if (revisedAmount != null) {
            if (revisedAmount.signum() < 0) {
                throw new IllegalArgumentException("Revised amount cannot be negative");
            }
            e.setSentAmount(revisedAmount);
        }
        e.setOhApprovalStatus(status);
        // A fresh OH decision supersedes any prior VP decision on the old amount
        e.setVpApprovalStatus("PENDING");
        return toDto(repo.save(e));
    }

    /** VP reviews an OH-approved entry: Approve or Reject (no amount revision). */
    public ExpenseItemDto setVpApproval(Long id, String status) {
        ExpenseItem e = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense item not found: " + id));
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            throw new IllegalArgumentException("status must be APPROVED or REJECTED");
        }
        if (!"APPROVED".equals(normalizeApproval(e.getOhApprovalStatus()))) {
            throw new IllegalArgumentException("Entry #" + id + " must be OH-approved before VP approval");
        }
        e.setVpApprovalStatus(status);
        return toDto(repo.save(e));
    }

    private String normalizeApproval(String status) {
        return (status == null || status.isBlank()) ? "PENDING" : status;
    }

    private void apply(ExpenseItem e, ExpenseItemDto dto) {
        if (dto.getProjectId() != null)        e.setProjectId(dto.getProjectId());
        if (dto.getCategory() != null)         e.setCategory(dto.getCategory().toUpperCase());
        if (dto.getDescription() != null)      e.setDescription(dto.getDescription());
        if (dto.getPartyName() != null)        e.setPartyName(dto.getPartyName());
        if (dto.getMonthYear() != null)        e.setMonthYear(dto.getMonthYear());
        if (dto.getRefNo() != null)            e.setRefNo(dto.getRefNo());
        if (dto.getPwjGross() != null)         e.setPwjGross(dto.getPwjGross());
        if (dto.getGstPercent() != null)       e.setGstPercent(dto.getGstPercent());
        if (dto.getPwjGstAmount() != null)     e.setPwjGstAmount(dto.getPwjGstAmount());
        if (dto.getPwjTotalPayable() != null)  e.setPwjTotalPayable(dto.getPwjTotalPayable());
        if (dto.getVendorGross() != null)      e.setVendorGross(dto.getVendorGross());
        if (dto.getVendorGstPercent() != null) e.setVendorGstPercent(dto.getVendorGstPercent());
        if (dto.getVendorGstAmount() != null)  e.setVendorGstAmount(dto.getVendorGstAmount());
        if (dto.getVendorTotalPayable() != null) e.setVendorTotalPayable(dto.getVendorTotalPayable());
        e.setPaymentDate(dto.getPaymentDate());
        if (dto.getPaymentAgainst() != null)   e.setPaymentAgainst(dto.getPaymentAgainst());
        if (dto.getPaidAmount() != null)       e.setPaidAmount(dto.getPaidAmount());
        if (dto.getPaidTo() != null)           e.setPaidTo(dto.getPaidTo());
        if (dto.getRemarks() != null)          e.setRemarks(dto.getRemarks());
    }

    private ExpenseItemDto toDto(ExpenseItem e) {
        return toDto(e, new HashMap<>());
    }

    private ExpenseItemDto toDto(ExpenseItem e, Map<String, BigDecimal[]> poCache) {
        ExpenseItemDto d = new ExpenseItemDto();
        d.setId(e.getId());
        d.setProjectId(e.getProjectId());
        d.setCategory(e.getCategory());
        d.setDescription(e.getDescription());
        d.setPartyName(e.getPartyName());
        d.setMonthYear(e.getMonthYear());
        d.setRefNo(e.getRefNo());
        d.setPwjGross(safe(e.getPwjGross()));
        d.setGstPercent(safe(e.getGstPercent()));
        d.setPwjGstAmount(safe(e.getPwjGstAmount()));
        d.setPwjTotalPayable(safe(e.getPwjTotalPayable()));
        d.setVendorGross(safe(e.getVendorGross()));
        d.setVendorGstPercent(safe(e.getVendorGstPercent()));
        d.setVendorGstAmount(safe(e.getVendorGstAmount()));
        d.setVendorTotalPayable(safe(e.getVendorTotalPayable()));
        d.setPaymentDate(e.getPaymentDate());
        d.setPaymentAgainst(e.getPaymentAgainst());
        d.setPaidAmount(safe(e.getPaidAmount()));
        d.setBalanceAsPerPwj(safe(e.getPwjTotalPayable()).subtract(safe(e.getPaidAmount())));
        d.setBalanceAsPerActual(safe(e.getVendorTotalPayable()).subtract(safe(e.getPaidAmount())));
        d.setPaidTo(e.getPaidTo());
        d.setRemarks(e.getRemarks());
        List<String> allowed = ALLOWED_CATEGORIES.get(orderTypePrefix(e.getPaymentAgainst()));
        d.setAllowedCategories(allowed != null ? allowed : List.of());
        d.setEligibleForPayment(Boolean.TRUE.equals(e.getEligibleForPayment()));
        d.setPaymentStatus(isNotSent(e.getPaymentStatus()) ? "NOT_SENT" : e.getPaymentStatus());
        d.setSentAmount(safe(e.getSentAmount()));
        d.setSentAt(e.getSentAt());
        d.setOhApprovalStatus(normalizeApproval(e.getOhApprovalStatus()));
        d.setVpApprovalStatus(normalizeApproval(e.getVpApprovalStatus()));
        if (e.getRefNo() != null && !e.getRefNo().isBlank()) {
            String key = e.getProjectId() + "|" + e.getRefNo();
            BigDecimal[] poTotals = poCache.computeIfAbsent(key, k -> new BigDecimal[]{
                    repo.sumPwjTotalPayableForPo(e.getProjectId(), e.getRefNo()),
                    repo.sumSentForPo(e.getProjectId(), e.getRefNo())
            });
            d.setPoValue(poTotals[0]);
            d.setPoBalanceRemaining(poTotals[0].subtract(poTotals[1]));
        }
        return d;
    }

    private BigDecimal safe(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    // A blank paymentStatus means this row predates the column being added — treat it as NOT_SENT
    private boolean isNotSent(String status) {
        return status == null || status.isBlank() || "NOT_SENT".equals(status);
    }
}
