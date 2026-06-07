package com.pwj.tracker.account.controller;

import com.pwj.tracker.account.repository.ExpenseItemRepository;
import com.pwj.tracker.model.Project;
import com.pwj.tracker.repository.ProjectRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Serves /api/projects for the account frontend — reads directly from PWJ's project table.
 */
@RestController
@RequestMapping("/api/projects")
public class AccountProjectController {

    private final ProjectRepository projectRepo;
    private final ExpenseItemRepository expenseRepo;

    private static final List<String> CATEGORIES =
            List.of("MATERIAL", "SUBCONTRACT", "CONSULTANTS", "LABOUR", "MISCELLANEOUS");

    public AccountProjectController(ProjectRepository projectRepo, ExpenseItemRepository expenseRepo) {
        this.projectRepo = projectRepo;
        this.expenseRepo = expenseRepo;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        return ResponseEntity.ok(
            projectRepo.findByActiveTrueOrderByNameAsc().stream().map(this::toMap).collect(Collectors.toList())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        Project p = projectRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
        return ResponseEntity.ok(toMap(p));
    }

    @GetMapping("/{id}/expense-summary")
    public ResponseEntity<Map<String, Object>> getExpenseSummary(@PathVariable Long id) {
        Project proj = projectRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));

        Map<String, BigDecimal[]> db = new HashMap<>();
        for (Object[] row : expenseRepo.getSummaryByProject(id)) {
            String cat    = (String) row[0];
            BigDecimal pwj    = dec(row[1]);
            BigDecimal paid   = dec(row[2]);
            BigDecimal vendor = dec(row[3]);
            db.put(cat, new BigDecimal[]{pwj, paid, vendor});
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totPwj = BigDecimal.ZERO, totPaid = BigDecimal.ZERO, totVendor = BigDecimal.ZERO;
        for (String cat : CATEGORIES) {
            BigDecimal[] vals = db.getOrDefault(cat, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal pwj = vals[0], paid = vals[1], vendor = vals[2];
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("category",      cat);
            row.put("pwjTotal",      pwj);
            row.put("actualPaid",    paid);
            row.put("vendorTotal",   vendor);
            row.put("balancePwj",    pwj.subtract(paid));
            row.put("balanceActual", vendor.subtract(paid));
            rows.add(row);
            totPwj = totPwj.add(pwj); totPaid = totPaid.add(paid); totVendor = totVendor.add(vendor);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectName",  proj.getName());
        result.put("client",       proj.getClientName());
        result.put("quoteGross",   proj.getTotalValue() != null ? proj.getTotalValue() : BigDecimal.ZERO);
        result.put("categories",   rows);
        result.put("totals", Map.of(
            "pwjTotal",      totPwj,
            "actualPaid",    totPaid,
            "vendorTotal",   totVendor,
            "balancePwj",    totPwj.subtract(totPaid),
            "balanceActual", totVendor.subtract(totPaid)
        ));
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> toMap(Project p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",             p.getId());
        m.put("name",           p.getName());
        m.put("client",         p.getClientName());
        m.put("location",       p.getLocation());
        m.put("description",    p.getDescription());
        m.put("clientGstNo",    p.getClientGstNo());
        m.put("clientAddress",  p.getClientAddress());
        m.put("billingAddress", p.getBillingAddress());
        m.put("status",         Boolean.TRUE.equals(p.getActive()) ? "active" : "inactive");
        m.put("quoteGross",     p.getQuoteValue());
        m.put("totalValue",     p.getTotalValue());
        return m;
    }

    private BigDecimal dec(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        return new BigDecimal(o.toString());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
    }
}
