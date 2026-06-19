package com.pwj.tracker.account.controller;

import com.pwj.tracker.account.dto.ProjectCollectionDto;
import com.pwj.tracker.account.repository.ProjectCollectionRepository;
import com.pwj.tracker.account.service.CollectionAlertService;
import com.pwj.tracker.account.service.ProjectCollectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/collections")
public class ProjectCollectionController {

    private final ProjectCollectionService service;
    private final CollectionAlertService alertService;
    private final ProjectCollectionRepository collectionRepo;

    public ProjectCollectionController(ProjectCollectionService service, CollectionAlertService alertService,
                                       ProjectCollectionRepository collectionRepo) {
        this.service        = service;
        this.alertService   = alertService;
        this.collectionRepo = collectionRepo;
    }

    @GetMapping("/project/{projectId}")
    public List<ProjectCollectionDto> getByProject(@PathVariable Long projectId) {
        return service.getByProject(projectId);
    }

    @GetMapping("/totals-by-project")
    public ResponseEntity<Map<Long, BigDecimal>> getTotalsByProject() {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        collectionRepo.sumCollectedGroupedByProject().forEach(r ->
            result.put(((Number) r[0]).longValue(), (BigDecimal) r[1])
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ProjectCollectionDto create(@RequestBody ProjectCollectionDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public ProjectCollectionDto update(@PathVariable Long id, @RequestBody ProjectCollectionDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/trigger-alerts")
    public ResponseEntity<Map<String, Object>> triggerAlerts() {
        int sent = alertService.triggerNow();
        return ResponseEntity.ok(Map.of(
            "status", "ok", "sent", sent,
            "message", sent > 0 ? sent + " alert email(s) sent" : "No collections with payment date found"
        ));
    }
}
