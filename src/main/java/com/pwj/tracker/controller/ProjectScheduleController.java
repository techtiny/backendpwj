package com.pwj.tracker.controller;

import com.pwj.tracker.dto.ApiResponse;
import com.pwj.tracker.dto.ProjectScheduleDto;
import com.pwj.tracker.model.ProjectSchedule;
import com.pwj.tracker.model.ScheduleActivity;
import com.pwj.tracker.repository.ProjectRepository;
import com.pwj.tracker.repository.ProjectScheduleRepository;
import com.pwj.tracker.repository.ScheduleActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Operations → Project Planning schedule for one project.
 * GET returns the header and all activity rows; PUT saves the whole schedule:
 * rows with a known id are updated, rows without one are created, and rows
 * missing from the request are deleted.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/schedule")
@RequiredArgsConstructor
public class ProjectScheduleController {

    private final ProjectRepository projectRepository;
    private final ProjectScheduleRepository scheduleRepository;
    private final ScheduleActivityRepository activityRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<ProjectScheduleDto> get(@PathVariable Long projectId) {
        requireProject(projectId);
        return ApiResponse.ok("Schedule fetched", load(projectId));
    }

    @PutMapping
    @Transactional
    public ApiResponse<ProjectScheduleDto> save(@PathVariable Long projectId, @RequestBody ProjectScheduleDto req) {
        requireProject(projectId);

        ProjectSchedule header = scheduleRepository.findByProjectId(projectId)
                .orElseGet(() -> ProjectSchedule.builder().projectId(projectId).build());
        header.setWoNumber(blankToNull(req.getWoNumber()));
        header.setStartDate(req.getStartDate());
        header.setCompletionDate(req.getCompletionDate());
        header.setSpocClient(blankToNull(req.getSpocClient()));
        header.setSpocContact(blankToNull(req.getSpocContact()));
        header.setHappizoRepresentative(blankToNull(req.getHappizoRepresentative()));
        header.setUpdatedBy(blankToNull(req.getUpdatedBy()));
        scheduleRepository.save(header);

        Map<Long, ScheduleActivity> existing = activityRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId)
                .stream().collect(Collectors.toMap(ScheduleActivity::getId, Function.identity()));

        List<ScheduleActivity> incoming = req.getActivities() != null ? req.getActivities() : List.of();
        Set<Long> kept = new HashSet<>();
        List<ScheduleActivity> toSave = new ArrayList<>();
        for (int i = 0; i < incoming.size(); i++) {
            ScheduleActivity in = incoming.get(i);
            // Only reuse an id that already belongs to this project — never let a
            // request overwrite another project's row.
            ScheduleActivity row = in.getId() != null ? existing.get(in.getId()) : null;
            if (row == null) row = new ScheduleActivity();
            else kept.add(row.getId());
            row.setProjectId(projectId);
            row.setBoqNo(blankToNull(in.getBoqNo()));
            row.setBoqDescription(blankToNull(in.getBoqDescription()));
            row.setDescription(blankToNull(in.getDescription()));
            row.setDependency(normaliseDependency(in.getDependency()));
            row.setDependencyTargetDate(in.getDependencyTargetDate());
            row.setMaterialDeliveryDate(in.getMaterialDeliveryDate());
            row.setDurationDays(in.getDurationDays());
            row.setPlannedStart(in.getPlannedStart());
            row.setPlannedFinish(in.getPlannedFinish());
            row.setActualStart(in.getActualStart());
            row.setActualFinish(in.getActualFinish());
            row.setStatus(normaliseStatus(in.getStatus()));
            row.setSortOrder(i);
            toSave.add(row);
        }
        existing.keySet().stream().filter(id -> !kept.contains(id)).forEach(activityRepository::deleteById);
        activityRepository.saveAll(toSave);

        return ApiResponse.ok("Schedule saved", load(projectId));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleError(RuntimeException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private ProjectScheduleDto load(Long projectId) {
        ProjectSchedule h = scheduleRepository.findByProjectId(projectId).orElse(null);
        ProjectScheduleDto dto = ProjectScheduleDto.builder()
                .projectId(projectId)
                .activities(activityRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId))
                .build();
        if (h != null) {
            dto.setWoNumber(h.getWoNumber());
            dto.setStartDate(h.getStartDate());
            dto.setCompletionDate(h.getCompletionDate());
            dto.setSpocClient(h.getSpocClient());
            dto.setSpocContact(h.getSpocContact());
            dto.setHappizoRepresentative(h.getHappizoRepresentative());
            dto.setUpdatedBy(h.getUpdatedBy());
            dto.setUpdatedAt(h.getUpdatedAt());
        }
        return dto;
    }

    private void requireProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) throw new RuntimeException("Project not found: " + projectId);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static final List<String> DEPENDENCIES = List.of("Architect", "Consultant", "Client");

    private static String normaliseDependency(String s) {
        if (s == null || s.isBlank()) return null;
        return DEPENDENCIES.stream().filter(d -> d.equalsIgnoreCase(s.trim())).findFirst().orElse(null);
    }

    private static String normaliseStatus(String s) {
        if (s == null || s.isBlank()) return null;
        String u = s.trim().toUpperCase();
        return u.equals("COMPLETED") || u.equals("NOT_COMPLETED") ? u : null;
    }
}
