package com.pwj.tracker.account.service;

import com.pwj.tracker.account.dto.ProjectCollectionDto;
import com.pwj.tracker.account.entity.ProjectCollection;
import com.pwj.tracker.account.repository.ProjectCollectionRepository;
import com.pwj.tracker.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProjectCollectionService {

    private final ProjectCollectionRepository collectionRepo;
    private final ProjectRepository projectRepo;

    public ProjectCollectionService(ProjectCollectionRepository collectionRepo, ProjectRepository projectRepo) {
        this.collectionRepo = collectionRepo;
        this.projectRepo    = projectRepo;
    }

    public List<ProjectCollectionDto> getByProject(Long projectId) {
        return collectionRepo.findByProjectIdOrderByStageAsc(projectId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public ProjectCollectionDto create(ProjectCollectionDto dto) {
        ProjectCollection e = new ProjectCollection();
        e.setProjectId(dto.getProjectId());
        e.setStage(dto.getStage());
        e.setAmount(dto.getAmount() != null ? dto.getAmount() : BigDecimal.ZERO);
        e.setCollectedAmt(dto.getCollectedAmt() != null ? dto.getCollectedAmt() : BigDecimal.ZERO);
        e.setCollectedDate(dto.getCollectedDate());
        e.setDueDate(dto.getDueDate());
        e.setPaymentDate(dto.getPaymentDate());
        e.setPaymentIntervalDays(dto.getPaymentIntervalDays());
        e.setAlertActive(dto.getPaymentDate() != null && dto.getPaymentIntervalDays() != null);
        return toDto(collectionRepo.save(e));
    }

    @Transactional
    public ProjectCollectionDto update(Long id, ProjectCollectionDto dto) {
        ProjectCollection e = collectionRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Collection not found: " + id));
        if (dto.getStage() != null)        e.setStage(dto.getStage());
        if (dto.getAmount() != null)       e.setAmount(dto.getAmount());
        if (dto.getCollectedAmt() != null) e.setCollectedAmt(dto.getCollectedAmt());
        e.setCollectedDate(dto.getCollectedDate());
        e.setDueDate(dto.getDueDate());
        e.setPaymentDate(dto.getPaymentDate());
        e.setPaymentIntervalDays(dto.getPaymentIntervalDays());
        if (dto.getAlertActive() != null) {
            e.setAlertActive(dto.getAlertActive());
        } else {
            e.setAlertActive(dto.getPaymentDate() != null && dto.getPaymentIntervalDays() != null);
        }
        return toDto(collectionRepo.save(e));
    }

    @Transactional
    public void delete(Long id) { collectionRepo.deleteById(id); }

    private ProjectCollectionDto toDto(ProjectCollection e) {
        BigDecimal amount    = e.getAmount()       != null ? e.getAmount()       : BigDecimal.ZERO;
        BigDecimal collected = e.getCollectedAmt() != null ? e.getCollectedAmt() : BigDecimal.ZERO;
        String projectName   = projectRepo.findById(e.getProjectId()).map(p -> p.getName()).orElse("Unknown");

        ProjectCollectionDto dto = new ProjectCollectionDto();
        dto.setId(e.getId());
        dto.setProjectId(e.getProjectId());
        dto.setProjectName(projectName);
        dto.setStage(e.getStage());
        dto.setAmount(amount);
        dto.setCollectedAmt(collected);
        dto.setCollectedDate(e.getCollectedDate());
        dto.setDueAmount(amount.subtract(collected));
        dto.setDueDate(e.getDueDate());
        dto.setPaymentDate(e.getPaymentDate());
        dto.setPaymentIntervalDays(e.getPaymentIntervalDays());
        dto.setAlertActive(Boolean.TRUE.equals(e.getAlertActive()));
        return dto;
    }
}
