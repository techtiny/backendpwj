package com.pwj.tracker.account.service;

import com.pwj.tracker.account.dto.ExpenseItemDto;
import com.pwj.tracker.account.entity.ExpenseItem;
import com.pwj.tracker.account.repository.ExpenseItemRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

@Service
public class ExpenseItemService {

    private final ExpenseItemRepository repo;

    public ExpenseItemService(ExpenseItemRepository repo) {
        this.repo = repo;
    }

    public List<ExpenseItemDto> getByProjectAndCategory(Long projectId, String category) {
        return repo.findByProjectIdAndCategoryOrderById(projectId, category.toUpperCase())
                .stream().map(this::toDto).toList();
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
        return d;
    }

    private BigDecimal safe(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
