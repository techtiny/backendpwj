package com.pwj.tracker.account.service;

import com.pwj.tracker.account.dto.DashboardStatsDto;
import com.pwj.tracker.account.repository.ExpenseItemRepository;
import com.pwj.tracker.account.repository.ProjectCollectionRepository;
import com.pwj.tracker.model.Project;
import com.pwj.tracker.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class DashboardService {

    private static final BigDecimal BUDGET_RATIO = new BigDecimal("0.80");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ProjectRepository projectRepo;
    private final ExpenseItemRepository expenseRepo;
    private final ProjectCollectionRepository collectionRepo;

    public DashboardService(ProjectRepository projectRepo,
                             ExpenseItemRepository expenseRepo,
                             ProjectCollectionRepository collectionRepo) {
        this.projectRepo    = projectRepo;
        this.expenseRepo    = expenseRepo;
        this.collectionRepo = collectionRepo;
    }

    public DashboardStatsDto getStats() {
        List<Project> projects = projectRepo.findAll();

        BigDecimal totalQuote = projects.stream()
                .map(p -> p.getTotalValue() != null ? p.getTotalValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCollection = projects.stream()
                .map(p -> collectionRepo.sumCollectedByProject(p.getId()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Object[]> expRows = expenseRepo.getExpenseBreakdownByProject();
        BigDecimal totalSpent = expRows.stream()
                .map(r -> dec(r[1]).add(dec(r[2])).add(dec(r[3])).add(dec(r[4])).add(dec(r[5])))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBudget = totalQuote.multiply(BUDGET_RATIO);

        long total     = projects.size();
        long active    = projects.stream().filter(p -> Boolean.TRUE.equals(p.getActive())).count();
        long completed = projects.stream().filter(p -> !Boolean.TRUE.equals(p.getActive())).count();

        BigDecimal profit = totalQuote.subtract(totalSpent);
        BigDecimal margin = totalQuote.compareTo(BigDecimal.ZERO) > 0
                ? profit.divide(totalQuote, 4, RoundingMode.HALF_UP).multiply(HUNDRED)
                : BigDecimal.ZERO;

        DashboardStatsDto stats = new DashboardStatsDto();
        stats.setTotalQuote(totalQuote);
        stats.setTotalBudget(totalBudget);
        stats.setTotalSpent(totalSpent);
        stats.setTotalCollection(totalCollection);
        stats.setTotalProjects(total);
        stats.setActiveProjects(active);
        stats.setCompletedProjects(completed);
        stats.setAvgProfitMargin(margin.setScale(1, RoundingMode.HALF_UP));
        stats.setCategoryBreakdown(buildCategoryBreakdown(expRows));
        stats.setProjectExpenses(buildProjectExpenses(projects, expRows));
        return stats;
    }

    private List<Map<String, Object>> buildCategoryBreakdown(List<Object[]> rows) {
        BigDecimal mat = BigDecimal.ZERO, lab = BigDecimal.ZERO,
                   sub = BigDecimal.ZERO, con = BigDecimal.ZERO, mis = BigDecimal.ZERO;
        for (Object[] r : rows) {
            mat = mat.add(dec(r[1])); lab = lab.add(dec(r[2]));
            sub = sub.add(dec(r[3])); con = con.add(dec(r[4])); mis = mis.add(dec(r[5]));
        }
        return List.of(
            map("name", "MATERIAL",     "value", mat),
            map("name", "LABOUR",       "value", lab),
            map("name", "SUBCONTRACT",  "value", sub),
            map("name", "CONSULTANTS",  "value", con),
            map("name", "MISCELLANEOUS","value", mis)
        );
    }

    private List<Map<String, Object>> buildProjectExpenses(List<Project> projects, List<Object[]> rows) {
        Map<Long, String> nameMap = new HashMap<>();
        projects.forEach(p -> nameMap.put(p.getId(), p.getName()));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] r : rows) {
            Long pid = r[0] instanceof Number ? ((Number) r[0]).longValue() : null;
            BigDecimal mat = dec(r[1]), lab = dec(r[2]), sub = dec(r[3]), con = dec(r[4]), mis = dec(r[5]);
            BigDecimal total = mat.add(lab).add(sub).add(con).add(mis);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name",          nameMap.getOrDefault(pid, "Project " + pid));
            item.put("MATERIAL",      mat);
            item.put("LABOUR",        lab);
            item.put("SUBCONTRACTOR", sub);
            item.put("OVERHEAD",      con.add(mis));
            item.put("total",         total);
            list.add(item);
        }
        return list;
    }

    private Map<String, Object> map(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1); m.put(k2, v2);
        return m;
    }

    private BigDecimal dec(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        return new BigDecimal(o.toString());
    }
}
