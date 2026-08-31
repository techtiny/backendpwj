package com.pwj.tracker.account.service;

import com.pwj.tracker.account.dto.FundMovementDto;
import com.pwj.tracker.account.entity.ExpenseItem;
import com.pwj.tracker.account.entity.FundMovement;
import com.pwj.tracker.account.repository.ExpenseItemRepository;
import com.pwj.tracker.account.repository.FundMovementRepository;
import com.pwj.tracker.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class FundMovementService {

    private final FundMovementRepository repo;
    private final ProjectRepository projectRepo;
    private final ExpenseItemRepository expenseRepo;

    public FundMovementService(FundMovementRepository repo, ProjectRepository projectRepo,
                                ExpenseItemRepository expenseRepo) {
        this.repo = repo;
        this.projectRepo = projectRepo;
        this.expenseRepo = expenseRepo;
    }

    public List<FundMovementDto> list(String direction) {
        Map<Long, String> names = new HashMap<>();
        projectRepo.findAll().forEach(p -> names.put(p.getId(), p.getName()));
        List<FundMovement> rows = (direction == null || direction.isBlank())
                ? repo.findAllByOrderByMovementDateDescIdDesc()
                : repo.findByDirectionOrderByMovementDateDescIdDesc(direction.trim().toUpperCase());
        return rows.stream().map(m -> toDto(m, names)).toList();
    }

    public FundMovementDto create(FundMovementDto dto) {
        String dir = dto.getDirection() == null ? "" : dto.getDirection().trim().toUpperCase();
        if (!"INFLOW".equals(dir) && !"OUTFLOW".equals(dir)) {
            throw new IllegalStateException("direction must be INFLOW or OUTFLOW");
        }
        BigDecimal amount = dto.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Amount must be greater than zero");
        }

        String party = dto.getParty() == null ? "" : dto.getParty().trim();
        Long projectId = dto.getProjectId();
        if (projectId != null) {
            String name = projectRepo.findById(projectId).map(com.pwj.tracker.model.Project::getName).orElse(null);
            if (name == null) throw new IllegalArgumentException("Project not found: " + projectId);
            if (party.isBlank()) party = name;
        }
        if (party.isBlank()) {
            throw new IllegalStateException("OUTFLOW".equals(dir)
                    ? "Select the project the payment was made to"
                    : "Enter a Source Type");
        }

        FundMovement m = new FundMovement();
        m.setDirection(dir);
        m.setMovementDate(dto.getMovementDate() != null ? dto.getMovementDate() : LocalDate.now());
        m.setParty(party);
        m.setProjectId(projectId);
        m.setAmount(amount);
        m.setMode(dto.getMode() == null || dto.getMode().isBlank() ? null : dto.getMode().trim());
        m.setRemarks(dto.getRemarks() == null || dto.getRemarks().isBlank() ? null : dto.getRemarks().trim());

        Map<Long, String> names = new HashMap<>();
        projectRepo.findAll().forEach(p -> names.put(p.getId(), p.getName()));
        return toDto(repo.save(m), names);
    }

    public void delete(Long id) {
        if (!repo.existsById(id)) throw new IllegalArgumentException("Fund movement not found: " + id);
        repo.deleteById(id);
    }

    private BigDecimal safe(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /** projectId -> [inflow, outflow] from all fund movements. */
    private Map<Long, BigDecimal[]> aggByProject() {
        Map<Long, BigDecimal[]> agg = new LinkedHashMap<>();
        for (Object[] row : repo.sumGroupedByProjectAndDirection()) {
            Long pid = ((Number) row[0]).longValue();
            String dir = String.valueOf(row[1]);
            BigDecimal sum = (BigDecimal) row[2];
            BigDecimal[] a = agg.computeIfAbsent(pid, k -> new BigDecimal[]{ BigDecimal.ZERO, BigDecimal.ZERO });
            if ("INFLOW".equals(dir)) a[0] = a[0].add(sum); else a[1] = a[1].add(sum);
        }
        return agg;
    }

    /** Per-project fund balance: total inflow received for the project minus total outflow paid to it. */
    public List<Map<String, Object>> balances() {
        Map<Long, String> names = new HashMap<>();
        projectRepo.findAll().forEach(p -> names.put(p.getId(), p.getName()));

        List<Map<String, Object>> out = new ArrayList<>();
        aggByProject().forEach((pid, a) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("projectId", pid);
            m.put("projectName", names.getOrDefault(pid, "Unknown"));
            m.put("inflow", a[0]);
            m.put("outflow", a[1]);
            m.put("available", a[0].subtract(a[1]));
            out.add(m);
        });
        out.sort((x, y) -> String.valueOf(x.get("projectName")).compareToIgnoreCase(String.valueOf(y.get("projectName"))));
        return out;
    }

    /**
     * Compares each project's available fund against today's bank-transfer demand (VP-approved
     * Send-for-Payment items sent today). Lists shortfalls and the surplus projects that can
     * cover them; the bank file is "eligible" only once every project is funded.
     */
    public Map<String, Object> paymentFunding() {
        LocalDate today = LocalDate.now();
        Map<Long, String> names = new HashMap<>();
        projectRepo.findAll().forEach(p -> names.put(p.getId(), p.getName()));

        // Today's demand per project
        Map<Long, BigDecimal> demand = new LinkedHashMap<>();
        for (ExpenseItem e : expenseRepo.findSentForPayment()) {
            String vp = e.getVpApprovalStatus() == null ? "" : e.getVpApprovalStatus().trim().toUpperCase();
            if (!"APPROVED".equals(vp)) continue;
            if (e.getSentAt() == null || !e.getSentAt().toLocalDate().equals(today)) continue;
            if (e.getProjectId() == null) continue;
            BigDecimal pay = e.getApprovedValue() != null ? e.getApprovedValue() : safe(e.getSentAmount());
            demand.merge(e.getProjectId(), pay, BigDecimal::add);
        }

        Map<Long, BigDecimal[]> agg = aggByProject();
        java.util.function.Function<Long, BigDecimal> availOf =
                pid -> { BigDecimal[] a = agg.get(pid); return a == null ? BigDecimal.ZERO : a[0].subtract(a[1]); };

        List<Map<String, Object>> projects = new ArrayList<>();
        BigDecimal totalShortfall = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> en : demand.entrySet()) {
            Long pid = en.getKey();
            BigDecimal dem = en.getValue();
            BigDecimal avail = availOf.apply(pid);
            BigDecimal shortfall = dem.subtract(avail).max(BigDecimal.ZERO);
            totalShortfall = totalShortfall.add(shortfall);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("projectId", pid);
            m.put("projectName", names.getOrDefault(pid, "Unknown"));
            m.put("available", avail);
            m.put("demand", dem);
            m.put("shortfall", shortfall);
            m.put("funded", shortfall.signum() == 0);
            projects.add(m);
        }
        projects.sort(Comparator.comparing(x -> (boolean) x.get("funded"))); // unfunded first

        // Surplus = available beyond that project's own demand — usable to cover other projects
        List<Map<String, Object>> surplus = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal[]> en : agg.entrySet()) {
            Long pid = en.getKey();
            BigDecimal avail = en.getValue()[0].subtract(en.getValue()[1]);
            BigDecimal ownDemand = demand.getOrDefault(pid, BigDecimal.ZERO);
            BigDecimal free = avail.subtract(ownDemand);
            if (free.signum() > 0) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("projectId", pid);
                m.put("projectName", names.getOrDefault(pid, "Unknown"));
                m.put("available", avail);
                m.put("free", free);
                surplus.add(m);
            }
        }
        surplus.sort((a, b) -> ((BigDecimal) b.get("free")).compareTo((BigDecimal) a.get("free")));

        boolean allFunded = projects.stream().allMatch(p -> (boolean) p.get("funded"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", today.toString());
        out.put("fundingInUse", repo.count() > 0);
        out.put("projects", projects);
        out.put("surplus", surplus);
        out.put("totalShortfall", totalShortfall);
        out.put("allFunded", allFunded);
        return out;
    }

    /** Move fund from one project to another — recorded as a paired outflow / inflow. */
    @Transactional
    public void transferFund(Long fromProjectId, Long toProjectId, BigDecimal amount, String remarks) {
        if (fromProjectId == null || toProjectId == null) throw new IllegalArgumentException("Both projects are required");
        if (fromProjectId.equals(toProjectId)) throw new IllegalStateException("Source and destination cannot be the same");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalStateException("Amount must be greater than zero");

        String fromName = projectRepo.findById(fromProjectId).map(com.pwj.tracker.model.Project::getName)
                .orElseThrow(() -> new IllegalArgumentException("Source project not found"));
        String toName = projectRepo.findById(toProjectId).map(com.pwj.tracker.model.Project::getName)
                .orElseThrow(() -> new IllegalArgumentException("Destination project not found"));

        BigDecimal[] a = aggByProject().get(fromProjectId);
        BigDecimal fromAvailable = a == null ? BigDecimal.ZERO : a[0].subtract(a[1]);
        if (amount.compareTo(fromAvailable) > 0) {
            throw new IllegalStateException(fromName + " has only " + fromAvailable + " available");
        }

        String note = remarks == null || remarks.isBlank() ? null : remarks.trim();
        FundMovement out = new FundMovement();
        out.setDirection("OUTFLOW"); out.setMovementDate(LocalDate.now()); out.setProjectId(fromProjectId);
        out.setParty(toName); out.setAmount(amount); out.setMode("Fund Transfer");
        out.setRemarks(note != null ? note : "Fund transfer to " + toName);

        FundMovement in = new FundMovement();
        in.setDirection("INFLOW"); in.setMovementDate(LocalDate.now()); in.setProjectId(toProjectId);
        in.setParty(fromName); in.setAmount(amount); in.setMode("Fund Transfer");
        in.setRemarks(note != null ? note : "Fund transfer from " + fromName);

        repo.save(out);
        repo.save(in);
    }

    private FundMovementDto toDto(FundMovement m, Map<Long, String> names) {
        FundMovementDto d = new FundMovementDto();
        d.setId(m.getId());
        d.setDirection(m.getDirection());
        d.setMovementDate(m.getMovementDate());
        d.setParty(m.getParty());
        d.setProjectId(m.getProjectId());
        d.setProjectName(m.getProjectId() != null ? names.get(m.getProjectId()) : null);
        d.setAmount(m.getAmount());
        d.setMode(m.getMode());
        d.setRemarks(m.getRemarks());
        d.setCreatedAt(m.getCreatedAt());
        return d;
    }
}
