package com.pwj.tracker.account.service;

import com.pwj.tracker.account.dto.PlannedFundMovementDto;
import com.pwj.tracker.account.entity.PlannedFundMovement;
import com.pwj.tracker.account.repository.PlannedFundMovementRepository;
import com.pwj.tracker.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Planned Inflow / Planned Outflow — forecasted fund movements, kept separate from
 * FundMovementService/FundMovement entirely. Payment Funding never reads from here.
 */
@Service
public class PlannedFundMovementService {

    private static final Set<String> SOURCE_TYPES = Set.of("LOAN", "CLIENT_PAYMENT", "REFUND", "OTHER");

    private final PlannedFundMovementRepository repo;
    private final ProjectRepository projectRepo;

    public PlannedFundMovementService(PlannedFundMovementRepository repo, ProjectRepository projectRepo) {
        this.repo = repo;
        this.projectRepo = projectRepo;
    }

    public List<PlannedFundMovementDto> list(String direction) {
        Map<Long, String> names = projectNames();
        List<PlannedFundMovement> rows = (direction == null || direction.isBlank())
                ? repo.findAllByOrderByMovementDateDescIdDesc()
                : repo.findByDirectionOrderByMovementDateDescIdDesc(direction.trim().toUpperCase());
        return rows.stream().map(m -> toDto(m, names)).toList();
    }

    public PlannedFundMovementDto create(PlannedFundMovementDto dto) {
        String dir = dto.getDirection() == null ? "" : dto.getDirection().trim().toUpperCase();
        if (!"INFLOW".equals(dir) && !"OUTFLOW".equals(dir)) {
            throw new IllegalStateException("direction must be INFLOW or OUTFLOW");
        }
        BigDecimal amount = dto.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Amount must be greater than zero");
        }
        String sourceType = dto.getSourceType() == null ? "" : dto.getSourceType().trim().toUpperCase();
        if (!SOURCE_TYPES.contains(sourceType)) {
            throw new IllegalStateException("Source Type must be one of Loan, Client Payment, Refund, Other");
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
                    ? "Select the project the payment is planned for"
                    : "Enter a Source Type description");
        }

        PlannedFundMovement m = new PlannedFundMovement();
        m.setDirection(dir);
        m.setMovementDate(dto.getMovementDate() != null ? dto.getMovementDate() : LocalDate.now());
        m.setParty(party);
        m.setProjectId(projectId);
        m.setAmount(amount);
        m.setMode(dto.getMode() == null || dto.getMode().isBlank() ? null : dto.getMode().trim());
        m.setSourceType(sourceType);
        m.setReferenceNo(dto.getReferenceNo() == null || dto.getReferenceNo().isBlank() ? null : dto.getReferenceNo().trim());
        m.setRemarks(dto.getRemarks() == null || dto.getRemarks().isBlank() ? null : dto.getRemarks().trim());

        return toDto(repo.save(m), projectNames());
    }

    public void delete(Long id) {
        if (!repo.existsById(id)) throw new IllegalArgumentException("Planned fund movement not found: " + id);
        repo.deleteById(id);
    }

    private Map<Long, String> projectNames() {
        Map<Long, String> names = new HashMap<>();
        projectRepo.findAll().forEach(p -> names.put(p.getId(), p.getName()));
        return names;
    }

    private PlannedFundMovementDto toDto(PlannedFundMovement m, Map<Long, String> names) {
        PlannedFundMovementDto d = new PlannedFundMovementDto();
        d.setId(m.getId());
        d.setDirection(m.getDirection());
        d.setMovementDate(m.getMovementDate());
        d.setParty(m.getParty());
        d.setProjectId(m.getProjectId());
        d.setProjectName(m.getProjectId() != null ? names.get(m.getProjectId()) : null);
        d.setAmount(m.getAmount());
        d.setMode(m.getMode());
        d.setSourceType(m.getSourceType());
        d.setReferenceNo(m.getReferenceNo());
        d.setRemarks(m.getRemarks());
        d.setCreatedAt(m.getCreatedAt());
        return d;
    }
}
