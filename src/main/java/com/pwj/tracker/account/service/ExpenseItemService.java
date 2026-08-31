package com.pwj.tracker.account.service;

import com.pwj.tracker.account.dto.ExpenseItemDto;
import com.pwj.tracker.account.dto.SendForPaymentRequest;
import com.pwj.tracker.account.entity.ExpenseItem;
import com.pwj.tracker.account.repository.ExpenseItemRepository;
import com.pwj.tracker.dto.PwjEntryResponse;
import com.pwj.tracker.model.Vendor;
import com.pwj.tracker.repository.ProjectRepository;
import com.pwj.tracker.repository.VendorRepository;
import com.pwj.tracker.service.PwjEntryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
public class ExpenseItemService {

    private final ExpenseItemRepository repo;
    private final ProjectRepository projectRepo;
    private final PwjEntryService pwjEntryService;
    private final VendorRepository vendorRepo;

    public ExpenseItemService(ExpenseItemRepository repo, ProjectRepository projectRepo,
                               PwjEntryService pwjEntryService, VendorRepository vendorRepo) {
        this.repo = repo;
        this.projectRepo = projectRepo;
        this.pwjEntryService = pwjEntryService;
        this.vendorRepo = vendorRepo;
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

    /**
     * Procurement: create and immediately send a payment request straight from an issued
     * PWJ entry — vendor, doc number and project are derived from the entry itself, so the
     * caller only supplies the amount. Reuses the same PO-value cap as the manual
     * Send-for-Payment flow (grouped by refNo == the PWJ doc number).
     */
    @Transactional
    public ExpenseItemDto sendPwjEntryForPayment(Long pwjEntryId, BigDecimal amount) {
        return sendPwjEntryForPayment(pwjEntryId, amount, null, null);
    }

    @Transactional
    public ExpenseItemDto sendPwjEntryForPayment(Long pwjEntryId, BigDecimal amount, String remarks) {
        return sendPwjEntryForPayment(pwjEntryId, amount, remarks, null, null);
    }

    @Transactional
    public ExpenseItemDto sendPwjEntryForPayment(Long pwjEntryId, BigDecimal amount, String remarks, String paymentMadeAgainst) {
        return sendPwjEntryForPayment(pwjEntryId, amount, remarks, paymentMadeAgainst, null);
    }

    @Transactional
    public ExpenseItemDto sendPwjEntryForPayment(Long pwjEntryId, BigDecimal amount, String remarks,
                                                 String paymentMadeAgainst, String paymentStage) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        PwjEntryResponse entry = pwjEntryService.getById(pwjEntryId);
        boolean issued = Boolean.TRUE.equals(entry.getPwjIssued())
                || (entry.getDocStatus() != null && "VP_APPROVED".equals(entry.getDocStatus().name()));
        if (!issued) {
            throw new IllegalArgumentException("PWJ entry #" + pwjEntryId + " has not been issued yet");
        }
        if (entry.getDocNumber() == null || entry.getDocNumber().isBlank()) {
            throw new IllegalArgumentException("PWJ entry #" + pwjEntryId + " has no document number");
        }
        if (entry.getProjectName() == null || entry.getProjectName().isBlank()) {
            throw new IllegalArgumentException("PWJ entry #" + pwjEntryId + " has no project name");
        }
        // The Account module's projects are a separate list from the PWJ tracker's free-text
        // project names — match by name the same way GET /expenses/pwj-docs?projectId= does in
        // reverse. An issued PO whose project was never set up on the Account side can't be sent.
        Long accountProjectId = projectRepo.findByNameIgnoreCase(entry.getProjectName().trim())
                .map(com.pwj.tracker.model.Project::getId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No Account project named \"" + entry.getProjectName() + "\" — ask Accounts to set it up first"));

        Map<String, Object> summary = pwjEntryService.getDocSummaries().stream()
                .filter(d -> entry.getDocNumber().equals(d.get("docNumber")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Could not resolve document totals for " + entry.getDocNumber()));

        BigDecimal poValue = toBigDecimal(summary.get("totalPayable"));
        BigDecimal gross    = toBigDecimal(summary.get("gross"));
        BigDecimal gstPct   = toBigDecimal(summary.get("gstPct"));
        BigDecimal gstAmt   = toBigDecimal(summary.get("gstAmount"));

        BigDecimal alreadySentPo = repo.sumSentForPo(accountProjectId, entry.getDocNumber());
        BigDecimal newTotalForPo = alreadySentPo.add(amount);
        if (newTotalForPo.compareTo(poValue) > 0) {
            throw new IllegalArgumentException(
                    "Sending " + amount + " would exceed the PO value (" + poValue + ") for " +
                    entry.getDocNumber() + " — already sent " + alreadySentPo);
        }

        ExpenseItem e = new ExpenseItem();
        e.setProjectId(accountProjectId);
        e.setCategory(defaultCategoryFor(entry.getPwjType()));
        e.setDescription(entry.getMaterialRequired());
        e.setPartyName(entry.getVendor());
        e.setRefNo(entry.getDocNumber());
        e.setPwjGross(gross);
        e.setGstPercent(gstPct);
        e.setPwjGstAmount(gstAmt);
        e.setPwjTotalPayable(poValue);
        e.setPaymentAgainst(entry.getPwjType());
        e.setEligibleForPayment(true);
        e.setSentAmount(amount);
        e.setPaymentStatus(newTotalForPo.compareTo(poValue) >= 0 ? "FULL_PAYMENT_SENT" : "PART_PAYMENT_SENT");
        e.setSentAt(java.time.LocalDateTime.now());
        if (remarks != null && !remarks.isBlank()) e.setRemarks(remarks.trim());
        if (paymentMadeAgainst != null && !paymentMadeAgainst.isBlank()) e.setPaymentMadeAgainst(paymentMadeAgainst.trim());
        if (paymentStage != null && !paymentStage.isBlank()) e.setPaymentStage(paymentStage.trim());

        ExpenseItemDto dto = toDto(repo.save(e));
        dto.setProjectName(entry.getProjectName());
        return dto;
    }

    /**
     * Procurement: how much of an issued PWJ doc's value is still available to send for
     * payment — poValue (the doc's total payable) minus everything already sent against
     * that doc number. Mirrors the PO-value cap enforced by {@link #sendPwjEntryForPayment}.
     * {@code resolved} is false when the doc totals or the Account-side project can't be
     * matched, in which case {@code available} is 0 and the real check happens on submit.
     */
    public Map<String, Object> getPwjEntryPaymentAvailability(Long pwjEntryId) {
        PwjEntryResponse entry = pwjEntryService.getById(pwjEntryId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("docNumber", entry.getDocNumber());
        out.put("vendor", entry.getVendor());
        out.put("poValue", BigDecimal.ZERO);
        out.put("alreadySent", BigDecimal.ZERO);
        out.put("available", BigDecimal.ZERO);
        out.put("resolved", false);

        boolean issued = Boolean.TRUE.equals(entry.getPwjIssued())
                || (entry.getDocStatus() != null && "VP_APPROVED".equals(entry.getDocStatus().name()));
        if (!issued
                || entry.getDocNumber() == null || entry.getDocNumber().isBlank()
                || entry.getProjectName() == null || entry.getProjectName().isBlank()) {
            return out;
        }

        Long accountProjectId = projectRepo.findByNameIgnoreCase(entry.getProjectName().trim())
                .map(com.pwj.tracker.model.Project::getId)
                .orElse(null);
        Map<String, Object> summary = pwjEntryService.getDocSummaries().stream()
                .filter(d -> entry.getDocNumber().equals(d.get("docNumber")))
                .findFirst()
                .orElse(null);
        if (summary != null) {
            out.put("poValue", toBigDecimal(summary.get("totalPayable")));
        }
        if (accountProjectId == null || summary == null) {
            return out;
        }

        BigDecimal poValue = toBigDecimal(summary.get("totalPayable"));
        BigDecimal alreadySent = repo.sumSentForPo(accountProjectId, entry.getDocNumber());
        BigDecimal available = poValue.subtract(alreadySent).max(BigDecimal.ZERO);
        out.put("poValue", poValue);
        out.put("alreadySent", alreadySent);
        out.put("available", available);
        out.put("resolved", true);
        return out;
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        return new BigDecimal(String.valueOf(v));
    }

    /** Cross-project tracker: every entry that has been sent for payment (part or full), newest first. */
    public List<ExpenseItemDto> getSentForPayment() {
        Map<Long, String> names = new HashMap<>();
        projectRepo.findAll().forEach(p -> names.put(p.getId(), p.getName()));
        Map<String, Vendor> vendorsByName = new HashMap<>();
        vendorRepo.findAll().forEach(v -> {
            if (v.getName() != null) vendorsByName.putIfAbsent(v.getName().trim().toLowerCase(), v);
        });
        Map<String, BigDecimal[]> poCache = new HashMap<>();
        return repo.findSentForPayment().stream().map(e -> {
            ExpenseItemDto d = toDto(e, poCache);
            d.setProjectName(names.getOrDefault(e.getProjectId(), "Unknown"));
            enrichBeneficiary(d, e.getPartyName() == null ? null
                    : vendorsByName.get(e.getPartyName().trim().toLowerCase()));
            return d;
        }).toList();
    }

    /** Fill the beneficiary bank fields on the DTO from the matched Vendor (name-based), for the bank export. */
    private void enrichBeneficiary(ExpenseItemDto d, Vendor v) {
        if (v == null) return;
        String acc  = trimToNull(v.getAccountNumber());
        String ifsc = trimToNull(v.getIfscCode());
        String bank = trimToNull(v.getBankName());
        // Fall back to the free-text bankDetails blob ("Bank | A/C No: 123 | IFSC: ABC0001234")
        if ((acc == null || ifsc == null || bank == null) && v.getBankDetails() != null) {
            for (String seg : v.getBankDetails().split("\\|")) {
                String s = seg.trim();
                if (s.isEmpty()) continue;
                var accM  = java.util.regex.Pattern.compile("A/C\\s*No[.:]?\\s*(.+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(s);
                var ifscM = java.util.regex.Pattern.compile("IFSC[.:]?\\s*([A-Za-z0-9]+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(s);
                if (accM.find())        { if (acc == null)  acc = accM.group(1).trim(); }
                else if (ifscM.find())  { if (ifsc == null) ifsc = ifscM.group(1).trim(); }
                else if (bank == null)  { bank = s; }
            }
        }
        d.setBenAccountNumber(acc);
        d.setBenIfscCode(ifsc);
        d.setBenBankName(bank);
        d.setBenEmail(trimToNull(v.getEmail()));
        String digits = v.getPhoneNumber() == null ? "" : v.getPhoneNumber().replaceAll("\\D", "");
        d.setBenMobile(digits.length() >= 10 && digits.length() <= 15 ? digits : null);
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // ── OH → Admin → VP approval on the sent amount — independent of Send for Payment itself ──

    /** OH reviews a sent entry: may revise sentAmount, then Approve or Reject. Resets Admin + VP. */
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
            recomputeDeductions(e);
        }
        e.setOhApprovalStatus(status);
        // A fresh OH decision supersedes any later decision on the old amount
        e.setAdminApprovalStatus("PENDING");
        e.setVpApprovalStatus("PENDING");
        return toDto(repo.save(e));
    }

    /** Admin reviews an OH-approved entry: Approve or Reject (no amount revision). Resets VP. */
    public ExpenseItemDto setAdminApproval(Long id, String status) {
        ExpenseItem e = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense item not found: " + id));
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            throw new IllegalArgumentException("status must be APPROVED or REJECTED");
        }
        if (!"APPROVED".equals(normalizeApproval(e.getOhApprovalStatus()))) {
            throw new IllegalArgumentException("Entry #" + id + " must be OH-approved before Admin approval");
        }
        e.setAdminApprovalStatus(status);
        e.setVpApprovalStatus("PENDING");
        return toDto(repo.save(e));
    }

    /** VP reviews an Admin-approved entry: Approve or Reject (no amount revision). */
    public ExpenseItemDto setVpApproval(Long id, String status) {
        ExpenseItem e = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense item not found: " + id));
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            throw new IllegalArgumentException("status must be APPROVED or REJECTED");
        }
        if (!"APPROVED".equals(normalizeApproval(e.getAdminApprovalStatus()))) {
            throw new IllegalArgumentException("Entry #" + id + " must be Admin-approved before VP approval");
        }
        e.setVpApprovalStatus(status);
        return toDto(repo.save(e));
    }

    /**
     * Set the deductions on a sent entry — TDS % (auto TDS Amt) plus a manual Deduction
     * amount entered by Admin / VP / OH — and recompute the Approved Value. Changing the
     * numbers resets Admin + VP approval to PENDING.
     */
    public ExpenseItemDto setDeductions(Long id, BigDecimal tdsPercent, BigDecimal deductionAmount) {
        ExpenseItem e = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense item not found: " + id));
        if (tdsPercent != null && tdsPercent.signum() < 0) {
            throw new IllegalArgumentException("TDS % cannot be negative");
        }
        if (deductionAmount != null && deductionAmount.signum() < 0) {
            throw new IllegalArgumentException("Deduction cannot be negative");
        }
        e.setTdsPercent(tdsPercent != null && tdsPercent.signum() == 0 ? null : tdsPercent);
        e.setDeductionAmount(deductionAmount != null && deductionAmount.signum() == 0 ? null : deductionAmount);
        recomputeDeductions(e);
        e.setAdminApprovalStatus("PENDING");
        e.setVpApprovalStatus("PENDING");
        return toDto(repo.save(e));
    }

    /**
     *   TDS Amt        = sentAmount * tds% / 100
     *   Deduction      = the manual amount entered by Admin / VP / OH
     *   Approved Value = sentAmount - TDS Amt - Deduction
     */
    private void recomputeDeductions(ExpenseItem e) {
        BigDecimal base = safe(e.getSentAmount());
        BigDecimal tdsAmt = e.getTdsPercent() == null ? BigDecimal.ZERO
                : base.multiply(e.getTdsPercent())
                      .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal deduction = safe(e.getDeductionAmount());
        e.setTdsAmount(tdsAmt);
        e.setApprovedValue(base.subtract(tdsAmt).subtract(deduction));
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
        if (dto.getPaymentMadeAgainst() != null) e.setPaymentMadeAgainst(dto.getPaymentMadeAgainst());
        if (dto.getPaymentStage() != null)     e.setPaymentStage(dto.getPaymentStage());
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
        d.setPaymentMadeAgainst(e.getPaymentMadeAgainst());
        d.setPaymentStage(e.getPaymentStage());
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
        d.setAdminApprovalStatus(normalizeApproval(e.getAdminApprovalStatus()));
        d.setVpApprovalStatus(normalizeApproval(e.getVpApprovalStatus()));
        d.setTdsPercent(e.getTdsPercent());
        d.setTdsAmount(e.getTdsAmount());
        d.setDeductionAmount(e.getDeductionAmount());
        d.setApprovedValue(e.getApprovedValue());
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
