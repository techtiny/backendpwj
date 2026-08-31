package com.pwj.tracker.account.service;

import com.pwj.tracker.account.dto.FundMovementDto;
import com.pwj.tracker.account.entity.FundMovement;
import com.pwj.tracker.account.repository.FundMovementRepository;
import com.pwj.tracker.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class FundMovementService {

    private final FundMovementRepository repo;
    private final ProjectRepository projectRepo;

    public FundMovementService(FundMovementRepository repo, ProjectRepository projectRepo) {
        this.repo = repo;
        this.projectRepo = projectRepo;
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

    /** Per-project fund balance: total inflow received for the project minus total outflow paid to it. */
    public List<Map<String, Object>> balances() {
        Map<Long, String> names = new HashMap<>();
        projectRepo.findAll().forEach(p -> names.put(p.getId(), p.getName()));

        Map<Long, BigDecimal[]> agg = new LinkedHashMap<>(); // projectId -> [inflow, outflow]
        for (Object[] row : repo.sumGroupedByProjectAndDirection()) {
            Long pid = ((Number) row[0]).longValue();
            String dir = String.valueOf(row[1]);
            BigDecimal sum = (BigDecimal) row[2];
            BigDecimal[] a = agg.computeIfAbsent(pid, k -> new BigDecimal[]{ BigDecimal.ZERO, BigDecimal.ZERO });
            if ("INFLOW".equals(dir)) a[0] = a[0].add(sum); else a[1] = a[1].add(sum);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        agg.forEach((pid, a) -> {
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
