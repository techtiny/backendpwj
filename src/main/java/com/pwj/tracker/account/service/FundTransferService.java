package com.pwj.tracker.account.service;

import com.pwj.tracker.account.dto.FundTransferDto;
import com.pwj.tracker.account.entity.FundTransfer;
import com.pwj.tracker.account.repository.FundTransferRepository;
import com.pwj.tracker.account.repository.ProjectCollectionRepository;
import com.pwj.tracker.model.Project;
import com.pwj.tracker.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class FundTransferService {

    private final FundTransferRepository transferRepo;
    private final ProjectRepository projectRepo;
    private final ProjectCollectionRepository collectionRepo;

    public FundTransferService(FundTransferRepository transferRepo,
                                ProjectRepository projectRepo,
                                ProjectCollectionRepository collectionRepo) {
        this.transferRepo  = transferRepo;
        this.projectRepo   = projectRepo;
        this.collectionRepo = collectionRepo;
    }

    public List<FundTransferDto> getAll() {
        Map<Long, String> names = new HashMap<>();
        projectRepo.findAll().forEach(p -> names.put(p.getId(), p.getName()));
        return transferRepo.findAllByOrderByTransferDateDescIdDesc()
                .stream().map(t -> toDto(t, names)).toList();
    }

    public BigDecimal availableBalance(Long projectId) {
        BigDecimal collected = collectionRepo.sumCollectedByProject(projectId);
        BigDecimal outgoing  = transferRepo.sumOutgoing(projectId);
        BigDecimal incoming  = transferRepo.sumIncoming(projectId);
        return safe(collected).subtract(safe(outgoing)).add(safe(incoming));
    }

    public FundTransferDto create(FundTransferDto dto) {
        Project from = projectRepo.findById(dto.getFromProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Source project not found"));
        Project to = projectRepo.findById(dto.getToProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Destination project not found"));

        if (from.getId().equals(to.getId()))
            throw new IllegalStateException("Source and destination project cannot be the same.");

        BigDecimal amount = dto.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalStateException("Transfer amount must be greater than zero.");

        FundTransfer t = new FundTransfer();
        t.setFromProjectId(from.getId());
        t.setToProjectId(to.getId());
        t.setAmount(amount);
        t.setTransferDate(dto.getTransferDate() != null ? dto.getTransferDate() : LocalDate.now());
        t.setRemarks(dto.getRemarks());

        Map<Long, String> names = Map.of(from.getId(), from.getName(), to.getId(), to.getName());
        return toDto(transferRepo.save(t), names);
    }

    public void delete(Long id) {
        if (!transferRepo.existsById(id)) throw new IllegalArgumentException("Transfer not found: " + id);
        transferRepo.deleteById(id);
    }

    public List<Map<String, Object>> getAllProjects() {
        List<Map<String, Object>> result = new ArrayList<>();
        projectRepo.findByActiveTrueOrderByNameAsc().forEach(p -> {
            BigDecimal available = availableBalance(p.getId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",               p.getId());
            m.put("name",             p.getName());
            m.put("collectionReceived", collectionRepo.sumCollectedByProject(p.getId()));
            m.put("availableBalance", available);
            result.add(m);
        });
        return result;
    }

    private FundTransferDto toDto(FundTransfer t, Map<Long, String> names) {
        FundTransferDto d = new FundTransferDto();
        d.setId(t.getId());
        d.setFromProjectId(t.getFromProjectId());
        d.setFromProjectName(names.getOrDefault(t.getFromProjectId(), "Unknown"));
        d.setToProjectId(t.getToProjectId());
        d.setToProjectName(names.getOrDefault(t.getToProjectId(), "Unknown"));
        d.setAmount(t.getAmount());
        d.setTransferDate(t.getTransferDate());
        d.setRemarks(t.getRemarks());
        return d;
    }

    private BigDecimal safe(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
